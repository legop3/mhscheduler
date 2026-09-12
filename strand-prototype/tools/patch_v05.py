from pathlib import Path

p = Path('app/src/main/java/com/openai/strandterrain/SafeActivity.java')
s = p.read_text()

def rep(old, new, count=1):
    global s
    if old not in s:
        raise RuntimeError(f'missing patch target: {old[:120]}')
    s = s.replace(old, new, count)

def section(start, end, new):
    global s
    i = s.index(start)
    j = s.index(end, i)
    s = s[:i] + new + s[j:]

# UI/version only; renderer architecture remains the proven v0.3 path.
rep('STRAND TERRAIN v0.3', 'STRAND TERRAIN v0.5')
rep('scanned moss/grass • soil • basalt • geological rocks', 'Iceland grassland • wet basalt • macro outcrops')
rep('v0.3 scanned ground • geological rock clusters', 'v0.5 Iceland geology • closer third-person camera')

# Camera / presentation.
rep('r.distance=Math.max(18,Math.min(82,r.distance));', 'r.distance=Math.max(11,Math.min(55,r.distance));')
rep('float wet,yaw=35,pitch=33,distance=46,px,pz,heading,moveAmount;', 'float wet,yaw=35,pitch=27,distance=24,px,pz,heading,moveAmount;')
rep('Matrix.perspectiveM(proj,0,52f,(float)w/Math.max(1,h),.35f,260f);', 'Matrix.perspectiveM(proj,0,50f,(float)w/Math.max(1,h),.35f,260f);')
rep('static final int MAX_ROCKS=150;', 'static final int MAX_ROCKS=220;')
rep('GLES20.glClearColor(.045f+.015f*(1-wet),.060f+.020f*(1-wet),.055f+.012f*(1-wet),1);',
    'GLES20.glClearColor(.40f-.08f*wet,.43f-.09f*wet,.42f-.08f*wet,1);')

section('        float height(float x,float z){', '        float moisture(float x,float z,float h,float lap){', '''        float height(float x,float z){
            float wx=(fbm(x*.012f+9,z*.012f-7,seed+41)-.5f)*18;
            float wz=(fbm(x*.012f-4,z*.012f+5,seed+53)-.5f)*18;
            float macro=(fbm((x+wx)*.018f,(z+wz)*.018f,seed)-.5f)*13.5f;
            float ridge=(1-Math.abs(noise((x+wx)*.047f+17,(z+wz)*.047f-9,seed+3)*2-1))*2.7f;
            float humm=(fbm(x*.105f+8,z*.105f-4,seed+9)-.48f)*1.7f;
            float main=z+11+8*(float)Math.sin(x*.04f+seed*.0007f);
            float valley=-5.4f*(float)Math.exp(-(main*main)/(175f));
            float trib=x-20f*(float)Math.sin(z*.031f+1.3f+seed*.0003f);
            float tributary=-1.8f*(float)Math.exp(-(trib*trib)/(55f));
            float shelfN=fbm((x+wx)*.0105f+21,(z+wz)*.0105f-13,seed+127);
            float shelf=clamp01((shelfN-.545f)*5.0f);
            shelf=shelf*shelf*(3f-2f*shelf);
            float shelves=shelf*6.6f;
            float rib=1f-Math.abs(noise((x+wx)*.027f-5,(z+wz)*.027f+4,seed+211)*2f-1f);
            float crags=Math.max(0,rib-.63f)*7.0f*shelf;
            float edge=(float)Math.sqrt(x*x+z*z)/72f;
            float rim=clamp01((edge-.60f)/.42f);
            rim=rim*rim*(3f-2f*rim);
            return macro+ridge+humm+valley+tributary+shelves+crags+rim*3.6f;
        }

''')

rep('float score=.48f*s+.28f*convex+.18f*(1-moist)+.20f*Math.max(0,patch-.55f)-.34f*open;',
    'float score=.58f*s+.34f*convex+.16f*(1-moist)+.20f*Math.max(0,patch-.53f)-.22f*open;')

section('        void buildRocks(){', '        float[] downhill(float x,float z){', '''        void buildRocks(){
            float[] out=new float[MAX_ROCKS*300*6]; int[] p={0}; int rocks=0;

            int formations=2+rng.nextInt(3);
            for(int f=0;f<formations && rocks<MAX_ROCKS-8;f++){
                float cx=0,cz=0; boolean ok=false;
                for(int a=0;a<90;a++){
                    cx=(rng.nextFloat()-.5f)*108; cz=(rng.nextFloat()-.5f)*108;
                    float d2=cx*cx+cz*cz;
                    float m=moisture(cx,cz,sampleHeight(cx,cz),0);
                    if(d2>420 && (rockScore(cx,cz,m)>.30f || slopeAt(cx,cz)>.38f)){ok=true;break;}
                }
                if(!ok)continue;
                int parts=2+rng.nextInt(3);
                float formationHeading=rng.nextFloat()*360f;
                for(int j=0;j<parts && rocks<MAX_ROCKS;j++){
                    float ang=(float)Math.toRadians(formationHeading+(j-parts*.5f)*18f+(rng.nextFloat()-.5f)*16f);
                    float off=(j-(parts-1)*.5f)*(3.0f+rng.nextFloat()*2.2f);
                    float x=cx+(float)Math.cos(ang)*off;
                    float z=cz+(float)Math.sin(ang)*off;
                    float rx=5.2f+rng.nextFloat()*6.5f;
                    float rz=4.0f+rng.nextFloat()*5.8f;
                    float ry=3.0f+rng.nextFloat()*5.5f;
                    addRock(out,p,x,sampleHeight(x,z)-ry*(.32f+rng.nextFloat()*.15f),z,rx,ry,rz,formationHeading+(rng.nextFloat()-.5f)*24f,rng.nextFloat());
                    rocks++;
                }
            }

            float[] ax=new float[24],az=new float[24]; int anchors=0;
            int desiredAnchors=11+rng.nextInt(7);
            for(int attempt=0;attempt<280 && anchors<desiredAnchors && rocks<MAX_ROCKS;attempt++){
                float x=(rng.nextFloat()-.5f)*114,z=(rng.nextFloat()-.5f)*114;
                if(x*x+z*z<95)continue;
                float moist=moisture(x,z,sampleHeight(x,z),0),score=rockScore(x,z,moist);
                if(score<.27f+rng.nextFloat()*.22f)continue;
                boolean far=true; for(int j=0;j<anchors;j++){float dx=x-ax[j],dz=z-az[j];if(dx*dx+dz*dz<72){far=false;break;}}
                if(!far)continue;
                float rx=1.5f+rng.nextFloat()*3.8f,rz=1.3f+rng.nextFloat()*3.8f,ry=1.0f+rng.nextFloat()*2.9f;
                addRock(out,p,x,sampleHeight(x,z)-ry*(.25f+rng.nextFloat()*.18f),z,rx,ry,rz,rng.nextFloat()*360f,rng.nextFloat());
                ax[anchors]=x;az[anchors]=z;anchors++;rocks++;
            }

            int clusters=10+rng.nextInt(6);
            for(int c=0;c<clusters && rocks<MAX_ROCKS-10;c++){
                float cx=0,cz=0; boolean ok=false;
                for(int attempt=0;attempt<70;attempt++){
                    cx=(rng.nextFloat()-.5f)*114;cz=(rng.nextFloat()-.5f)*114;
                    float m=moisture(cx,cz,sampleHeight(cx,cz),0),score=rockScore(cx,cz,m);
                    if(score>.19f+rng.nextFloat()*.20f){ok=true;break;}
                }
                if(!ok)continue;
                int members=3+rng.nextInt(5); float radius=2.2f+rng.nextFloat()*6.5f;
                float[] down=downhill(cx,cz); float baseHeading=(float)Math.toDegrees(Math.atan2(down[0],down[1]));
                for(int j=0;j<members && rocks<MAX_ROCKS;j++){
                    float ang=rng.nextFloat()*(float)Math.PI*2f;
                    float rad=radius*(float)Math.pow(rng.nextFloat(),1.65);
                    float x=cx+(float)Math.cos(ang)*rad,z=cz+(float)Math.sin(ang)*rad;
                    float rx=.55f+rng.nextFloat()*1.75f,rz=.48f+rng.nextFloat()*1.85f,ry=.42f+rng.nextFloat()*1.55f;
                    addRock(out,p,x,sampleHeight(x,z)-ry*(.22f+rng.nextFloat()*.22f),z,rx,ry,rz,baseHeading+(rng.nextFloat()-.5f)*55f,rng.nextFloat());
                    rocks++;
                }
            }

            int screeZones=3+rng.nextInt(3);
            for(int q=0;q<screeZones && rocks<MAX_ROCKS-14;q++){
                float sx=0,sz=0; boolean ok=false;
                for(int a=0;a<80;a++){
                    sx=(rng.nextFloat()-.5f)*108;sz=(rng.nextFloat()-.5f)*108;
                    if(slopeAt(sx,sz)>.43f && rockScore(sx,sz,.25f)>.37f){ok=true;break;}
                }
                if(!ok)continue;
                float[] down=downhill(sx,sz); int n=8+rng.nextInt(9); float length=7+rng.nextFloat()*13;
                for(int j=0;j<n && rocks<MAX_ROCKS;j++){
                    float t=(j+rng.nextFloat())/n*length;
                    float side=(rng.nextFloat()-.5f)*(2.8f+3.8f*t/length);
                    float x=sx+down[0]*t-down[1]*side,z=sz+down[1]*t+down[0]*side;
                    float sc=.18f+rng.nextFloat()*.52f;
                    addRock(out,p,x,sampleHeight(x,z)-sc*(.18f+rng.nextFloat()*.18f),z,sc*(.8f+rng.nextFloat()*.7f),sc*(.65f+rng.nextFloat()*.7f),sc*(.8f+rng.nextFloat()*.7f),rng.nextFloat()*360,rng.nextFloat());
                    rocks++;
                }
            }

            int embedded=28+rng.nextInt(17);
            for(int i=0;i<embedded && rocks<MAX_ROCKS;i++){
                for(int attempt=0;attempt<35;attempt++){
                    float x=(rng.nextFloat()-.5f)*114,z=(rng.nextFloat()-.5f)*114;
                    if(x*x+z*z<45)continue;
                    float m=moisture(x,z,sampleHeight(x,z),0),score=rockScore(x,z,m);
                    if(score<.32f+rng.nextFloat()*.25f)continue;
                    float sc=.26f+rng.nextFloat()*.78f;
                    addRock(out,p,x,sampleHeight(x,z)-sc*(.30f+rng.nextFloat()*.28f),z,sc*(.8f+rng.nextFloat()*.6f),sc*(.6f+rng.nextFloat()*.7f),sc*(.8f+rng.nextFloat()*.6f),rng.nextFloat()*360,rng.nextFloat());
                    rocks++;break;
                }
            }

            rockCount=p[0]/6;
            ByteBuffer b=ByteBuffer.allocateDirect(p[0]*4).order(ByteOrder.nativeOrder());
            rockVB=b.asFloatBuffer(); rockVB.put(out,0,p[0]).position(0);
        }

''')

section('        void addRock(float[] o,int[] p,float x,float y,float z,float rx,float ry,float rz,float heading,float shape){', '        void addTri(float[] o,int[] p,float[] a,float[] b,float[] c){', '''        void addRock(float[] o,int[] p,float x,float y,float z,float rx,float ry,float rz,float heading,float shape){
            final int seg=12;
            if(p[0]+300*6>o.length)return;
            float[][][] ring=new float[4][seg][3];
            float[] radius={.78f,1.00f,.76f,.34f};
            float[] yy={.02f,.31f,.68f,.94f};
            float rot=(float)Math.toRadians(heading),cs=(float)Math.cos(rot),sn=(float)Math.sin(rot);
            for(int r=0;r<4;r++)for(int i=0;i<seg;i++){
                float a=(float)(Math.PI*2*i/seg)+(r%2)*.055f;
                float wob=1f+.10f*(float)Math.sin(i*2.37f+shape*8.1f+r*.7f)+.055f*(float)Math.cos(i*4.11f+shape*5.3f);
                float lx=(float)Math.cos(a)*rx*radius[r]*wob;
                float lz=(float)Math.sin(a)*rz*radius[r]*(1f+.045f*(float)Math.sin(i*3.2f+shape*9f));
                ring[r][i][0]=x+lx*cs-lz*sn;
                ring[r][i][1]=y+ry*(yy[r]+.035f*(float)Math.sin(i*2.8f+r+shape*7f));
                ring[r][i][2]=z+lx*sn+lz*cs;
            }
            for(int r=0;r<3;r++)for(int i=0;i<seg;i++){
                int j=(i+1)%seg;
                addTri(o,p,ring[r][i],ring[r][j],ring[r+1][j]);
                addTri(o,p,ring[r][i],ring[r+1][j],ring[r+1][i]);
            }
            float[] top={x,y+ry*1.04f,z};
            float[] bottom={x,y,z};
            for(int i=0;i<seg;i++){
                int j=(i+1)%seg;
                addTri(o,p,ring[3][i],ring[3][j],top);
                addTri(o,p,ring[0][j],ring[0][i],bottom);
            }
        }

''')

# Existing shader programs only: same samplers and draw passes, different material balance.
rep('texture2D(uMoss,vPos.xz/15.0).rgb', 'texture2D(uMoss,vPos.xz/3.0).rgb')
rep('float mw=clamp((.48+.62*vMoist)*(1.0-rw)*(1.0-smoothstep(.18,.50,slope)),0.0,1.0);',
    'float saturated=smoothstep(.72,.98,vMoist);float mw=clamp((.78+.28*vMoist)*(1.0-rw)*(1.0-smoothstep(.22,.58,slope)),0.0,1.0);mw*=1.0-.42*saturated;')
rep('float light=.30+.70*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);',
    'float light=.47+.53*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);')
rep('vec3 fogCol=mix(vec3(.16,.19,.17),vec3(.105,.125,.12),uWet);',
    'vec3 fogCol=mix(vec3(.30,.33,.31),vec3(.22,.25,.24),uWet);')
rep('float l=.27+.73*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);',
    'float l=.42+.58*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);')
rep('GLES20.glUniform3f(ouColor,.105f,.115f,.108f);', 'GLES20.glUniform3f(ouColor,.21f,.22f,.20f);')

p.write_text(s)
print('v0.5 patch complete')
