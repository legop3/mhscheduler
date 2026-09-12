from pathlib import Path

p = Path('app/src/main/java/com/openai/strandterrain/SafeActivity.java')
s = p.read_text()

def rep(old, new, count=1):
    global s
    if old not in s:
        raise RuntimeError(f'missing v0.6.1 target: {old[:160]}')
    s = s.replace(old, new, count)

# Identity / HUD
rep('STRAND TERRAIN v0.6', 'STRAND TERRAIN v0.6.1')
rep('v0.6 scanned rocks • grass clumps • carved valleys', 'v0.6.1 denser geology • softer grass • deeper contrast')
# patch_v06 intentionally carries a second HUD line as a literal newline; collapse it back
# into a valid single Java string for this build.
s = s.replace('v0.6.1 denser geology • softer grass • deeper contrast\nCC0 scans: Poly Haven',
              'v0.6.1 denser geology • softer grass • deeper contrast • Poly Haven CC0')

# Cooler/darker overcast background so terrain does not wash into the sky.
rep('GLES20.glClearColor(.49f-.10f*wet,.52f-.11f*wet,.51f-.10f*wet,1);',
    'GLES20.glClearColor(.365f-.065f*wet,.405f-.072f*wet,.405f-.070f*wet,1);')

# Grass object color: lighter green and much less black-looking in shade.
rep('GLES20.glUniform3f(ouColor,.15f-.025f*wet,.285f-.045f*wet,.105f-.020f*wet);',
    'GLES20.glUniform3f(ouColor,.235f-.030f*wet,.385f-.050f*wet,.165f-.024f*wet);')

# Softer, broader, shorter clumps and reduced count. They should read as turf, not needles.
rep('final int target=1350;', 'final int target=920;')
rep('float gh=.13f+gr.nextFloat()*(.11f+.16f*m);',
    'float gh=.085f+gr.nextFloat()*(.065f+.085f*m);')
rep('float gw=.045f+gr.nextFloat()*.045f;',
    'float gw=.075f+gr.nextFloat()*.070f;')
rep('addGrassPlane(out,p,x,h+.012f,z,gh*(.82f+gr.nextFloat()*.22f),gw*.88f,gr.nextFloat()*180f);',
    'addGrassPlane(out,p,x,h+.010f,z,gh*(.78f+gr.nextFloat()*.18f),gw*.95f,gr.nextFloat()*180f);')
rep('float leanX=(float)Math.sin(a)*h*.10f,leanZ=-(float)Math.cos(a)*h*.10f;',
    'float leanX=(float)Math.sin(a)*h*.055f,leanZ=-(float)Math.cos(a)*h*.055f;')
rep('float[] tl={x-dx*.22f+leanX,y+h,z-dz*.22f+leanZ},tr={x+dx*.22f+leanX,y+h,z+dz*.22f+leanZ};',
    'float[] tl={x-dx*.48f+leanX,y+h,z-dz*.48f+leanZ},tr={x+dx*.48f+leanX,y+h,z+dz*.48f+leanZ};')

# Add small high-frequency erosion breakup only where shelf geology is active.
rep('float micro=(fbm(x*.11f+8,z*.11f-4,seed+9)-.5f)*1.15f;',
    'float micro=(fbm(x*.11f+8,z*.11f-4,seed+9)-.5f)*1.15f;\n            float erosionDetail=(fbm(x*.225f-19,z*.225f+11,seed+733)-.5f)*1.05f*(.18f+.82f*shelfMask);')
rep('return base+valleyCut+drainage+ribs+micro+rim*4.0f;',
    'return base+valleyCut+drainage+ribs+micro+erosionDetail+rim*4.0f;')

# Denser geology and deeper embedding. This directly addresses the sparse/floating look.
rep('int formations=2+rng.nextInt(2);', 'int formations=3+rng.nextInt(2);')
rep('int pieces=1+rng.nextInt(2);', 'int pieces=1+rng.nextInt(3);')
rep('sampleHeight(x,z)-sy*(.36f+rng.nextFloat()*.13f)',
    'sampleHeight(x,z)-sy*(.54f+rng.nextFloat()*.14f)')
rep('sampleHeight(x,z)-sy*.35f', 'sampleHeight(x,z)-sy*.52f')
rep('int desiredAnchors=13+rng.nextInt(7);', 'int desiredAnchors=18+rng.nextInt(7);')
rep('sampleHeight(x,z)-sy*(.22f+rng.nextFloat()*.16f)',
    'sampleHeight(x,z)-sy*(.31f+rng.nextFloat()*.15f)')
rep('int clusters=12+rng.nextInt(6);', 'int clusters=18+rng.nextInt(7);')
rep('sampleHeight(x,z)-sy*(.18f+rng.nextFloat()*.18f)',
    'sampleHeight(x,z)-sy*(.25f+rng.nextFloat()*.18f)')

# Ground contrast: stronger macro breakup and less aggressive gray fog near the camera.
rep('float macro=.78+.26*texture2D(uGrass,vPos.xz/17.0).g;',
    'float macro=.70+.38*texture2D(uGrass,vPos.xz/15.0).g;')
rep('float light=.53+.47*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);',
    'float light=.43+.57*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);')
rep('float fog=smoothstep(45.0,112.0,d);', 'float fog=smoothstep(62.0,138.0,d);')
rep('vec3 fogCol=mix(vec3(.45,.48,.46),vec3(.31,.34,.33),uWet);',
    'vec3 fogCol=mix(vec3(.355,.395,.385),vec3(.255,.290,.285),uWet);')

# Rock shading: a little more local contrast and less flat ambient.
rep('float l=.47+.53*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);',
    'float l=.38+.62*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);')

p.write_text(s)
print('patched v0.6.1:', p)
