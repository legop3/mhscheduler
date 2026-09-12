from pathlib import Path

p = Path('app/src/main/java/com/openai/strandterrain/SafeActivity.java')
s = p.read_text()

def rep(old, new, count=1):
    global s
    if old not in s:
        raise RuntimeError(f'missing v0.6 patch target: {old[:140]}')
    s = s.replace(old, new, count)

def section(start, end, new):
    global s
    i = s.index(start)
    j = s.index(end, i)
    s = s[:i] + new + s[j:]

# --- Product/UI identity ---
rep('STRAND TERRAIN v0.5', 'STRAND TERRAIN v0.6')
rep('Iceland grassland • wet basalt • macro outcrops', 'Iceland grass • scanned geology • erosion')
rep('v0.5 Iceland geology • closer third-person camera', 'v0.6 scanned rocks • grass clumps • carved valleys\nCC0 scans: Poly Haven')

# --- Closer third-person composition ---
rep('r.distance=Math.max(11,Math.min(55,r.distance));', 'r.distance=Math.max(9,Math.min(48,r.distance));')
rep('float wet,yaw=35,pitch=27,distance=24,px,pz,heading,moveAmount;', 'float wet,yaw=35,pitch=24,distance=19,px,pz,heading,moveAmount;')
rep('GLES20.glClearColor(.40f-.08f*wet,.43f-.09f*wet,.42f-.08f*wet,1);',
    'GLES20.glClearColor(.49f-.10f*wet,.52f-.11f*wet,.51f-.10f*wet,1);')

# --- Buffers/resources: preserve the two existing GL programs ---
rep('FloatBuffer terrainVB,rockVB,cubeVB;', 'FloatBuffer terrainVB,rockVB,cubeVB,grassVB;')
rep('int terrainCount,rockCount,cubeCount;', 'int terrainCount,rockCount,cubeCount,grassCount;')
rep('int taPos,taNormal,taMoist,taRock,tuMvp,tuWet,tuCam,tuMoss,tuDirt,tuRockTex;',
    'int taPos,taNormal,taMoist,taRock,tuMvp,tuWet,tuCam,tuMoss,tuGrass,tuDirt,tuRockTex;')
rep('int mossTex,dirtTex,rockTex;', 'int mossTex,grassTex,dirtTex,rockTex;\n        float[][] scannedOutcrops=new float[0][],scannedRocks=new float[0][];')

rep('tuMoss=GLES20.glGetUniformLocation(terrainProgram,"uMoss");\n                tuDirt=',
    'tuMoss=GLES20.glGetUniformLocation(terrainProgram,"uMoss");\n                tuGrass=GLES20.glGetUniformLocation(terrainProgram,"uGrass");\n                tuDirt=')
rep('mossTex=loadTexture(R.drawable.aerial_grass_rock_diff_1k);\n                dirtTex=loadTexture(R.drawable.dirt_diff_1k);\n                rockTex=loadTexture(R.drawable.rock_ground_diff_1k);',
    'mossTex=loadTexture(R.drawable.aerial_grass_rock_diff_1k);\n                grassTex=loadTexture(R.drawable.leafy_grass_diff_1k);\n                dirtTex=loadTexture(R.drawable.dirt_diff_1k);\n                rockTex=loadTexture(R.drawable.rock_ground_diff_1k);\n                scannedOutcrops=loadMeshSet(R.raw.strand_outcrops);\n                scannedRocks=loadMeshSet(R.raw.strand_rocks);')

# Binary scanned-mesh loader. Big-endian matches Python struct output.
insert_after = '''        int loadTexture(int resId){
'''
idx = s.index(insert_after)
# Insert helper immediately before loadTexture.
helper = '''        float[][] loadMeshSet(int resId){
            try{
                java.io.DataInputStream in=new java.io.DataInputStream(new java.io.BufferedInputStream(getResources().openRawResource(resId)));
                int meshes=in.readInt();
                if(meshes<1||meshes>64){in.close();return new float[0][];}
                float[][] out=new float[meshes][];
                for(int m=0;m<meshes;m++){
                    int vertices=in.readInt();
                    if(vertices<3||vertices>300000){in.close();return new float[0][];}
                    float[] a=new float[vertices*6];
                    for(int i=0;i<a.length;i++)a[i]=in.readFloat();
                    out[m]=a;
                }
                in.close(); return out;
            }catch(Throwable t){return new float[0][];}
        }

'''
s = s[:idx] + helper + s[idx:]

# Four terrain samplers remain well below ES2's minimum sampler budget.
rep('bindTexture(0,mossTex); bindTexture(1,dirtTex); bindTexture(2,rockTex);\n            GLES20.glUniform1i(tuMoss,0); GLES20.glUniform1i(tuDirt,1); GLES20.glUniform1i(tuRockTex,2);',
    'bindTexture(0,mossTex); bindTexture(1,dirtTex); bindTexture(2,rockTex); bindTexture(3,grassTex);\n            GLES20.glUniform1i(tuMoss,0); GLES20.glUniform1i(tuDirt,1); GLES20.glUniform1i(tuRockTex,2); GLES20.glUniform1i(tuGrass,3);')

# Grass is an optional object-buffer draw using the existing objProgram.
rep('drawTerrain(cx,cy,cz);\n            drawRocks();', 'drawTerrain(cx,cy,cz);\n            drawGrass();\n            drawRocks();')

draw_grass = '''
        void drawGrass(){
            if(grassVB==null||grassCount<=0)return;
            GLES20.glUseProgram(objProgram);
            GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0);
            GLES20.glUniform1f(ouWet,wet);
            GLES20.glUniform1f(ouTextured,0f);
            bindTexture(2,rockTex); GLES20.glUniform1i(ouRockTex,2);
            Matrix.setIdentityM(model,0); GLES20.glUniformMatrix4fv(ouModel,1,false,model,0);
            GLES20.glUniform3f(ouColor,.15f-.025f*wet,.285f-.045f*wet,.105f-.020f*wet);
            grassVB.position(0); GLES20.glEnableVertexAttribArray(oaPos); GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,grassVB);
            grassVB.position(3); GLES20.glEnableVertexAttribArray(oaNormal); GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,grassVB);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,grassCount);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        }

'''
marker='        void drawRocks(){\n'
pos=s.index(marker)
s=s[:pos]+draw_grass+s[pos:]

# --- More geological heightfield: flatter valley floor, cut drainage, broken shelves/ribs ---
section('        float height(float x,float z){', '        float moisture(float x,float z,float h,float lap){', '''        float height(float x,float z){
            float wx=(fbm(x*.0115f+9,z*.0115f-7,seed+41)-.5f)*16;
            float wz=(fbm(x*.0115f-4,z*.0115f+5,seed+53)-.5f)*16;
            float macro=(fbm((x+wx)*.017f,(z+wz)*.017f,seed)-.5f)*11.5f;
            float broad=(fbm(x*.0068f-17,z*.0068f+23,seed+301)-.5f)*7.0f;
            float main=z+9+7.5f*(float)Math.sin(x*.038f+seed*.0007f);
            float valleyMask=(float)Math.exp(-(main*main)/(315f));
            float valleyCut=-4.7f*valleyMask;
            float floorNoise=(fbm(x*.045f+3,z*.045f-5,seed+91)-.5f)*1.25f;
            float base=macro+broad;
            float flattened=-3.0f+floorNoise+base*.24f;
            base=lerp(base,flattened,clamp01(valleyMask*.72f));

            float trib1=x-18f*(float)Math.sin(z*.030f+1.3f+seed*.0003f);
            float trib2=x+27f-12f*(float)Math.sin(z*.042f-.5f+seed*.0005f);
            float drainage=-1.8f*(float)Math.exp(-(trib1*trib1)/34f)-1.15f*(float)Math.exp(-(trib2*trib2)/27f);

            float shelfN=fbm((x+wx)*.015f+21,(z+wz)*.015f-13,seed+127);
            float shelfMask=clamp01((shelfN-.54f)*4.8f)*(1f-.70f*valleyMask);
            shelfMask=shelfMask*shelfMask*(3f-2f*shelfMask);
            float terrace=Math.round(base/1.45f)*1.45f;
            base=lerp(base,terrace,shelfMask*.38f);

            float ridge=1f-Math.abs(noise((x+wx)*.030f-5,(z+wz)*.030f+4,seed+211)*2f-1f);
            float ribs=Math.max(0,ridge-.60f)*8.5f*shelfMask;
            float micro=(fbm(x*.11f+8,z*.11f-4,seed+9)-.5f)*1.15f;

            float edge=(float)Math.sqrt(x*x+z*z)/73f;
            float rim=clamp01((edge-.60f)/.40f); rim=rim*rim*(3f-2f*rim);
            return base+valleyCut+drainage+ribs+micro+rim*4.0f;
        }

''')

# Slightly more exposed geology on convex/steep ground, still preserve green valley lanes.
rep('float score=.58f*s+.34f*convex+.16f*(1-moist)+.20f*Math.max(0,patch-.53f)-.22f*open;',
    'float score=.61f*s+.36f*convex+.14f*(1-moist)+.18f*Math.max(0,patch-.52f)-.27f*open;')

# --- Replace v0.5 dome geology with scanned modules and multi-scale rock ecology ---
section('        void buildRocks(){', '        float[] downhill(float x,float z){', '''        void buildRocks(){
            float[] out=new float[180000*6]; int[] p={0}; int rocks=0;

            // Macro geology: scanned cliff faces, deep-buried and grouped along outer slope transitions.
            int formations=2+rng.nextInt(2);
            for(int f=0;f<formations;f++){
                float cx=0,cz=0; boolean ok=false;
                for(int a=0;a<120;a++){
                    cx=(rng.nextFloat()-.5f)*108; cz=(rng.nextFloat()-.5f)*108;
                    float r2=cx*cx+cz*cz;
                    if(r2<650)continue;
                    float m=moisture(cx,cz,sampleHeight(cx,cz),0);
                    if(slopeAt(cx,cz)>.28f || rockScore(cx,cz,m)>.32f){ok=true;break;}
                }
                if(!ok)continue;
                float[] down=downhill(cx,cz);
                float heading=(float)Math.toDegrees(Math.atan2(down[0],down[1]))+90f;
                int pieces=1+rng.nextInt(2);
                for(int j=0;j<pieces;j++){
                    float side=(j-(pieces-1)*.5f)*(5.0f+rng.nextFloat()*2f);
                    float x=cx-down[1]*side,z=cz+down[0]*side;
                    float sx=8.5f+rng.nextFloat()*5.0f;
                    float sz=6.0f+rng.nextFloat()*4.0f;
                    float sy=4.0f+rng.nextFloat()*3.0f;
                    float[] mesh=pickMesh(scannedOutcrops);
                    if(mesh!=null)addMeshInstance(out,p,mesh,x,sampleHeight(x,z)-sy*(.36f+rng.nextFloat()*.13f),z,sx,sy,sz,heading+(rng.nextFloat()-.5f)*18f);
                    else addRock(out,p,x,sampleHeight(x,z)-sy*.35f,z,sx*.55f,sy,sz*.55f,heading,rng.nextFloat());
                    rocks++;
                }
            }

            // Large landmark boulders. Scanned shape, widely spaced, substantial burial.
            float[] ax=new float[28],az=new float[28]; int anchors=0;
            int desiredAnchors=13+rng.nextInt(7);
            for(int attempt=0;attempt<360 && anchors<desiredAnchors;attempt++){
                float x=(rng.nextFloat()-.5f)*114,z=(rng.nextFloat()-.5f)*114;
                if(x*x+z*z<85)continue;
                float moist=moisture(x,z,sampleHeight(x,z),0),score=rockScore(x,z,moist);
                if(score<.24f+rng.nextFloat()*.25f)continue;
                boolean far=true; for(int j=0;j<anchors;j++){float dx=x-ax[j],dz=z-az[j];if(dx*dx+dz*dz<58){far=false;break;}}
                if(!far)continue;
                float sx=2.0f+rng.nextFloat()*3.2f,sz=1.7f+rng.nextFloat()*3.0f,sy=1.2f+rng.nextFloat()*2.5f;
                float[] mesh=pickMesh(scannedRocks);
                if(mesh!=null)addMeshInstance(out,p,mesh,x,sampleHeight(x,z)-sy*(.22f+rng.nextFloat()*.16f),z,sx,sy,sz,rng.nextFloat()*360f);
                else addRock(out,p,x,sampleHeight(x,z)-sy*.24f,z,sx*.5f,sy,sz*.5f,rng.nextFloat()*360f,rng.nextFloat());
                ax[anchors]=x;az[anchors]=z;anchors++;rocks++;
            }

            // Medium clusters create the missing middle scale in the scene.
            int clusters=12+rng.nextInt(6);
            for(int c=0;c<clusters;c++){
                float cx=0,cz=0; boolean ok=false;
                for(int a=0;a<80;a++){
                    cx=(rng.nextFloat()-.5f)*114; cz=(rng.nextFloat()-.5f)*114;
                    float m=moisture(cx,cz,sampleHeight(cx,cz),0);
                    if(rockScore(cx,cz,m)>.16f+rng.nextFloat()*.23f){ok=true;break;}
                }
                if(!ok)continue;
                float[] down=downhill(cx,cz);
                float baseHeading=(float)Math.toDegrees(Math.atan2(down[0],down[1]));
                int members=3+rng.nextInt(5); float radius=2.2f+rng.nextFloat()*5.6f;
                for(int j=0;j<members;j++){
                    float ang=rng.nextFloat()*(float)Math.PI*2f;
                    float rad=radius*(float)Math.pow(rng.nextFloat(),1.7);
                    float x=cx+(float)Math.cos(ang)*rad,z=cz+(float)Math.sin(ang)*rad;
                    float sx=.75f+rng.nextFloat()*1.55f,sz=.65f+rng.nextFloat()*1.55f,sy=.48f+rng.nextFloat()*1.25f;
                    float[] mesh=pickMesh(scannedRocks);
                    if(mesh!=null)addMeshInstance(out,p,mesh,x,sampleHeight(x,z)-sy*(.18f+rng.nextFloat()*.18f),z,sx,sy,sz,baseHeading+(rng.nextFloat()-.5f)*58f);
                    else addRock(out,p,x,sampleHeight(x,z)-sy*.2f,z,sx*.5f,sy,sz*.5f,baseHeading,rng.nextFloat());
                    rocks++;
                }
            }

            // Scree/rubble: tiny forms stay procedural because silhouette matters less at this scale.
            int screeZones=4+rng.nextInt(3);
            for(int q=0;q<screeZones;q++){
                float sx=0,sz=0; boolean ok=false;
                for(int a=0;a<90;a++){
                    sx=(rng.nextFloat()-.5f)*108; sz=(rng.nextFloat()-.5f)*108;
                    if(slopeAt(sx,sz)>.38f && rockScore(sx,sz,.25f)>.32f){ok=true;break;}
                }
                if(!ok)continue;
                float[] down=downhill(sx,sz); int n=10+rng.nextInt(10); float length=8+rng.nextFloat()*13;
                for(int j=0;j<n;j++){
                    float t=(j+rng.nextFloat())/n*length;
                    float side=(rng.nextFloat()-.5f)*(2.5f+4.5f*t/length);
                    float x=sx+down[0]*t-down[1]*side,z=sz+down[1]*t+down[0]*side;
                    float sc=.16f+rng.nextFloat()*.42f;
                    addRock(out,p,x,sampleHeight(x,z)-sc*.12f,z,sc*(.8f+rng.nextFloat()*.7f),sc*(.55f+rng.nextFloat()*.55f),sc*(.8f+rng.nextFloat()*.7f),rng.nextFloat()*360f,rng.nextFloat());
                    rocks++;
                }
            }

            // Embedded stones create transition between grass and larger geology.
            int embedded=38+rng.nextInt(20);
            for(int i=0;i<embedded;i++){
                for(int a=0;a<35;a++){
                    float x=(rng.nextFloat()-.5f)*114,z=(rng.nextFloat()-.5f)*114;
                    if(x*x+z*z<36)continue;
                    float m=moisture(x,z,sampleHeight(x,z),0),score=rockScore(x,z,m);
                    if(score<.25f+rng.nextFloat()*.30f)continue;
                    float sc=.26f+rng.nextFloat()*.72f;
                    addRock(out,p,x,sampleHeight(x,z)-sc*(.30f+rng.nextFloat()*.25f),z,sc*(.8f+rng.nextFloat()*.6f),sc*(.52f+rng.nextFloat()*.55f),sc*(.8f+rng.nextFloat()*.6f),rng.nextFloat()*360f,rng.nextFloat());
                    rocks++;break;
                }
            }

            rockCount=p[0]/6;
            ByteBuffer b=ByteBuffer.allocateDirect(p[0]*4).order(ByteOrder.nativeOrder());
            rockVB=b.asFloatBuffer(); rockVB.put(out,0,p[0]).position(0);
        }

        float[] pickMesh(float[][] set){
            if(set==null||set.length==0)return null;
            return set[rng.nextInt(set.length)];
        }

        void addMeshInstance(float[] o,int[] p,float[] mesh,float x,float y,float z,float sx,float sy,float sz,float heading){
            if(mesh==null||p[0]+mesh.length>o.length)return;
            float a=(float)Math.toRadians(heading),cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            for(int i=0;i<mesh.length;i+=6){
                float lx=mesh[i]*sx,ly=mesh[i+1]*sy,lz=mesh[i+2]*sz;
                float wx=x+lx*cs-lz*sn,wz=z+lx*sn+lz*cs;
                float nx=mesh[i+3]/Math.max(.001f,sx),ny=mesh[i+4]/Math.max(.001f,sy),nz=mesh[i+5]/Math.max(.001f,sz);
                float rnx=nx*cs-nz*sn,rnz=nx*sn+nz*cs;
                float len=(float)Math.sqrt(rnx*rnx+ny*ny+rnz*rnz); if(len<.001f)len=1;
                int k=p[0]; o[k]=wx;o[k+1]=y+ly;o[k+2]=wz;o[k+3]=rnx/len;o[k+4]=ny/len;o[k+5]=rnz/len;p[0]+=6;
            }
        }

''')

# Build the safe grass layer after geology, using only the existing object shader.
rep('            buildRocks();\n        }', '            buildRocks();\n            buildGrass();\n        }')

grass_methods = '''
        void buildGrass(){
            final int target=1350;
            float[] out=new float[target*4*3*6]; int[] p={0}; int made=0;
            Random gr=new Random(seed*92821L+731);
            for(int attempt=0;attempt<9000 && made<target;attempt++){
                float x=(gr.nextFloat()-.5f)*116,z=(gr.nextFloat()-.5f)*116;
                float slope=slopeAt(x,z),h=sampleHeight(x,z),m=moisture(x,z,h,0),rock=rockScore(x,z,m);
                if(slope>.34f || rock>.54f)continue;
                if(gr.nextFloat()>.70f+.25f*m)continue;
                float gh=.13f+gr.nextFloat()*(.11f+.16f*m);
                float gw=.045f+gr.nextFloat()*.045f;
                addGrassPlane(out,p,x,h+.012f,z,gh,gw,gr.nextFloat()*180f);
                addGrassPlane(out,p,x,h+.012f,z,gh*(.82f+gr.nextFloat()*.22f),gw*.88f,gr.nextFloat()*180f);
                made++;
            }
            grassCount=p[0]/6;
            ByteBuffer b=ByteBuffer.allocateDirect(p[0]*4).order(ByteOrder.nativeOrder());
            grassVB=b.asFloatBuffer(); grassVB.put(out,0,p[0]).position(0);
        }

        void addGrassPlane(float[] o,int[] p,float x,float y,float z,float h,float w,float heading){
            if(p[0]+36>o.length)return;
            float a=(float)Math.toRadians(heading),dx=(float)Math.cos(a)*w,dz=(float)Math.sin(a)*w;
            float leanX=(float)Math.sin(a)*h*.10f,leanZ=-(float)Math.cos(a)*h*.10f;
            float[] bl={x-dx,y,z-dz},br={x+dx,y,z+dz};
            float[] tl={x-dx*.22f+leanX,y+h,z-dz*.22f+leanZ},tr={x+dx*.22f+leanX,y+h,z+dz*.22f+leanZ};
            addTri(o,p,bl,br,tr); addTri(o,p,bl,tr,tl);
        }

'''
marker='        void buildCube(){\n'
pos=s.index(marker)
s=s[:pos]+grass_methods+s[pos:]

# --- Terrain surface: separate grass and moss; drainage becomes darker soil; rock exposure stays geological. ---
section('        final String TFS=', '        final String OVS=', '''        final String TFS="precision mediump float;uniform sampler2D uMoss;uniform sampler2D uGrass;uniform sampler2D uDirt;uniform sampler2D uRockTex;uniform float uWet;uniform vec3 uCam;varying vec3 vPos;varying vec3 vN;varying float vMoist;varying float vRock;void main(){vec3 N=normalize(vN);float slope=1.0-clamp(N.y,0.0,1.0);vec3 grass=texture2D(uGrass,vPos.xz/2.45).rgb;vec3 moss=texture2D(uMoss,vPos.xz/2.20).rgb;vec3 dirt=texture2D(uDirt,vPos.xz/2.70).rgb;vec3 rock=texture2D(uRockTex,vPos.xz/2.05).rgb;float macro=.78+.26*texture2D(uGrass,vPos.xz/17.0).g;float steep=smoothstep(.20,.58,slope);float rw=clamp(vRock+steep*.68,0.0,1.0);float wetZone=smoothstep(.46,.90,vMoist);float gw=(1.0-rw)*(1.0-steep)*(.92-.40*wetZone);float mw=(1.0-rw)*(1.0-steep)*(.10+.76*wetZone);float dw=(1.0-rw)*(.07+.48*wetZone+.12*smoothstep(.18,.45,slope));float sum=max(.001,rw+gw+mw+dw);rw/=sum;gw/=sum;mw/=sum;dw/=sum;dirt*=mix(vec3(1.0),vec3(.62,.60,.52),wetZone);vec3 col=(grass*gw+moss*mw+dirt*dw+rock*rw)*macro;float light=.53+.47*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);col*=light;float localWet=uWet*(.34+.66*vMoist);col*=1.0-localWet*.23;vec3 V=normalize(uCam-vPos);vec3 H=normalize(V+normalize(vec3(-.46,.83,.31)));float spec=pow(max(dot(N,H),0.0),32.0)*localWet*(.08+.17*rw);col+=vec3(spec);float d=distance(uCam,vPos);float fog=smoothstep(45.0,112.0,d);vec3 fogCol=mix(vec3(.45,.48,.46),vec3(.31,.34,.33),uWet);gl_FragColor=vec4(mix(col,fogCol,fog),1.0);}";
''')

# Rock object shader: softer overcast ambient and neutral mossy basalt; prevent bright/white undersides.
section('        final String OFS=', '    }\n}', '''        final String OFS="precision mediump float;uniform vec3 uColor;uniform float uWet;uniform float uTextured;uniform sampler2D uRockTex;varying vec3 vN;varying vec3 vP;void main(){vec3 N=normalize(vN);vec2 uv;if(abs(N.y)>.55)uv=vP.xz*.30;else if(abs(N.x)>abs(N.z))uv=vP.zy*.30;else uv=vP.xy*.30;vec3 tex=texture2D(uRockTex,uv).rgb*vec3(.82,.86,.80);vec3 base=mix(uColor,tex,uTextured);float l=.47+.53*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);vec3 c=base*l;c*=1.0-uWet*.16*uTextured;gl_FragColor=vec4(c,1.0);}";
''')

p.write_text(s)
print('patched v0.6:', p)
