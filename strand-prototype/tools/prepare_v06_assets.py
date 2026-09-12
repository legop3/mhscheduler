from pathlib import Path
from urllib.request import Request, urlopen
from urllib.parse import urlparse
import json, struct, shutil

import numpy as np
from PIL import Image, ImageEnhance
import trimesh

ROOT = Path('.')
DRAW = ROOT / 'app/src/main/res/drawable-nodpi'
RAW = ROOT / 'app/src/main/res/raw'
TMP = Path('/tmp/strand-v06')
DRAW.mkdir(parents=True, exist_ok=True)
RAW.mkdir(parents=True, exist_ok=True)
TMP.mkdir(parents=True, exist_ok=True)
UA = 'StrandTerrainPrototype/0.6.1 (+https://polyhaven.com)'


def http_bytes(url):
    req = Request(url, headers={'User-Agent': UA})
    with urlopen(req, timeout=120) as r:
        return r.read()


def download(url, path):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.stat().st_size > 0:
        return path
    data = http_bytes(url)
    path.write_bytes(data)
    return path


def download_texture(url, name):
    return download(url, TMP / name)


def polyhaven_gltf(asset_id, resolution='1k'):
    files = json.loads(http_bytes(f'https://api.polyhaven.com/files/{asset_id}').decode('utf-8'))
    root = files.get('gltf') or {}
    if not root:
        raise RuntimeError(f'{asset_id}: no glTF package')
    available = [k for k, v in root.items() if isinstance(v, dict)]
    res = resolution if resolution in available else next((x for x in ('1k','2k','4k','8k') if x in available), available[0])
    block = (root.get(res) or {}).get('gltf') or {}
    main_url = block.get('url')
    if not main_url:
        raise RuntimeError(f'{asset_id}: no glTF URL at {res}')
    folder = TMP / 'models' / asset_id
    folder.mkdir(parents=True, exist_ok=True)
    main_name = Path(urlparse(main_url).path).name or f'{asset_id}.gltf'
    main = download(main_url, folder / main_name)
    includes = block.get('include') or {}
    if isinstance(includes, dict):
        for rel, info in includes.items():
            if isinstance(info, dict) and info.get('url'):
                download(info['url'], folder / rel)
    return main


def scene_to_mesh(loaded):
    if isinstance(loaded, trimesh.Trimesh):
        return loaded.copy()
    if hasattr(loaded, 'to_geometry'):
        try:
            g = loaded.to_geometry()
            if isinstance(g, trimesh.Trimesh):
                return g
        except Exception:
            pass
    meshes = []
    if isinstance(loaded, trimesh.Scene):
        try:
            for node in loaded.graph.nodes_geometry:
                transform, geom_name = loaded.graph[node]
                g = loaded.geometry[geom_name].copy()
                g.apply_transform(transform)
                meshes.append(g)
        except Exception:
            meshes = [g.copy() for g in loaded.geometry.values()]
    if not meshes:
        raise RuntimeError('no mesh geometry in downloaded model')
    return trimesh.util.concatenate(meshes)


def simplify(mesh, target_faces):
    mesh = mesh.copy()
    mesh.remove_unreferenced_vertices()
    if len(mesh.faces) <= target_faces:
        return mesh
    last = None
    for kwargs in ({'face_count': target_faces}, {'target_reduction': 1.0 - target_faces / len(mesh.faces)}):
        try:
            out = mesh.simplify_quadric_decimation(**kwargs)
            if out is not None and len(out.faces) > 0:
                out.remove_unreferenced_vertices()
                return out
        except Exception as e:
            last = e
    raise RuntimeError(f'mesh simplification failed ({len(mesh.faces)} -> {target_faces} faces): {last}')


def normalize_mesh(mesh):
    m = mesh.copy()
    v = np.asarray(m.vertices, dtype=np.float64).copy()
    mn = v.min(axis=0); mx = v.max(axis=0)
    v[:,0] -= (mn[0] + mx[0]) * 0.5
    v[:,2] -= (mn[2] + mx[2]) * 0.5
    v[:,1] -= mn[1]
    span_x = max(1e-6, mx[0] - mn[0])
    span_z = max(1e-6, mx[2] - mn[2])
    scale = max(span_x, span_z)
    v /= scale
    m.vertices = v
    m.remove_unreferenced_vertices()
    _ = m.vertex_normals
    return m


def components(asset_id, target_faces, max_components=1):
    main = polyhaven_gltf(asset_id, '1k')
    loaded = trimesh.load(main, force='scene', process=False)
    whole = scene_to_mesh(loaded)
    try:
        parts = list(whole.split(only_watertight=False))
    except Exception:
        parts = [whole]
    parts = [p for p in parts if len(p.faces) >= 40]
    if not parts:
        parts = [whole]
    parts.sort(key=lambda m: len(m.faces), reverse=True)
    chosen = []
    for p in parts[:max_components]:
        chosen.append(normalize_mesh(simplify(p, target_faces)))
    print(asset_id, 'components', len(chosen), 'faces', [len(x.faces) for x in chosen])
    return chosen


def write_meshbin(path, meshes):
    path = Path(path)
    with path.open('wb') as f:
        f.write(struct.pack('>i', len(meshes)))
        for mesh in meshes:
            verts = np.asarray(mesh.vertices, dtype=np.float32)
            norms = np.asarray(mesh.vertex_normals, dtype=np.float32)
            faces = np.asarray(mesh.faces, dtype=np.int64)
            count = int(faces.size)
            f.write(struct.pack('>i', count))
            for idx in faces.reshape(-1):
                p = verts[idx]; n = norms[idx]
                f.write(struct.pack('>6f', float(p[0]), float(p[1]), float(p[2]), float(n[0]), float(n[1]), float(n[2])))
    print('wrote', path, path.stat().st_size, 'bytes')


# --- Ground textures ---
leafy = download_texture('https://dl.polyhaven.org/file/ph-assets/Textures/jpg/4k/leafy_grass/leafy_grass_diff_4k.jpg', 'leafy_grass.jpg')
moss = download_texture('https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/aerial_grass_rock/aerial_grass_rock_diff_1k.jpg', 'aerial_grass_rock.jpg')
dirt = download_texture('https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/dirt/dirt_diff_1k.jpg', 'dirt.jpg')
rock = download_texture('https://dl.polyhaven.org/file/ph-assets/Textures/jpg/1k/aerial_rocks_01/aerial_rocks_01_diff_1k.jpg', 'aerial_rocks_01.jpg')

def save_grade(src, dest, size=(1024,1024), color=1.0, bright=1.0, contrast=1.0, rgb=(1,1,1)):
    im = Image.open(src).convert('RGB').resize(size, Image.Resampling.LANCZOS)
    im = ImageEnhance.Color(im).enhance(color)
    r,g,b = im.split()
    r = ImageEnhance.Brightness(r).enhance(rgb[0])
    g = ImageEnhance.Brightness(g).enhance(rgb[1])
    b = ImageEnhance.Brightness(b).enhance(rgb[2])
    im = Image.merge('RGB',(r,g,b))
    im = ImageEnhance.Brightness(im).enhance(bright)
    im = ImageEnhance.Contrast(im).enhance(contrast)
    im.save(dest, 'JPEG', quality=89, optimize=True)

# Stronger but still natural Icelandic green; preserve dark detail for contrast.
save_grade(leafy, DRAW/'leafy_grass_diff_1k.jpg', color=1.28, contrast=1.10, rgb=(0.86,1.22,0.95))
save_grade(moss, DRAW/'aerial_grass_rock_diff_1k.jpg', color=1.10, bright=.94, contrast=1.08, rgb=(.92,1.10,.95))
shutil.copyfile(dirt, DRAW/'dirt_diff_1k.jpg')
save_grade(rock, DRAW/'rock_ground_diff_1k.jpg', color=.90, bright=.91, contrast=1.15, rgb=(.94,.98,.96))

# --- Scanned geometry ---
outcrops = []
outcrops += components('rock_face_01', 850, 1)
outcrops += components('rock_face_02', 850, 1)
rocks = []
rocks += components('boulder_01', 420, 1)
rocks += components('rock_moss_set_01', 240, 6)

write_meshbin(RAW/'strand_outcrops.bin', outcrops)
write_meshbin(RAW/'strand_rocks.bin', rocks)

print('v0.6.1 assets ready:', len(outcrops), 'outcrops,', len(rocks), 'rock templates')
