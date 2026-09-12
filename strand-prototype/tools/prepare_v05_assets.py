from pathlib import Path
from PIL import Image, ImageEnhance

out = Path('app/src/main/res/drawable-nodpi')
out.mkdir(parents=True, exist_ok=True)

grass = Image.open('/tmp/strand-assets/leafy_grass.jpg').convert('RGB').resize((1024,1024), Image.Resampling.LANCZOS)
grass = ImageEnhance.Color(grass).enhance(1.18)
r,g,b = grass.split()
r = ImageEnhance.Brightness(r).enhance(0.90)
g = ImageEnhance.Brightness(g).enhance(1.16)
b = ImageEnhance.Brightness(b).enhance(0.98)
grass = Image.merge('RGB',(r,g,b))
grass = ImageEnhance.Contrast(grass).enhance(1.06)
grass.save(out/'aerial_grass_rock_diff_1k.jpg', 'JPEG', quality=90, optimize=True)

rock = Image.open('/tmp/strand-assets/mossy_rock.jpg').convert('RGB').resize((1024,1024), Image.Resampling.LANCZOS)
rock = ImageEnhance.Brightness(rock).enhance(1.18)
rock = ImageEnhance.Contrast(rock).enhance(1.08)
rock = ImageEnhance.Color(rock).enhance(0.92)
rock.save(out/'rock_ground_diff_1k.jpg', 'JPEG', quality=90, optimize=True)

print('prepared', out/'aerial_grass_rock_diff_1k.jpg', out/'rock_ground_diff_1k.jpg')
