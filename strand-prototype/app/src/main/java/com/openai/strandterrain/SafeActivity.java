package com.openai.strandterrain;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

public class SafeActivity extends Activity {
    TerrainView terrainView;
    TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        FrameLayout root = new FrameLayout(this);
        terrainView = new TerrainView();
        root.addView(terrainView, new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(24,18,24,18); panel.setBackgroundColor(0x99040A07);
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(14); panel.addView(status);
        LinearLayout row = new LinearLayout(this);
        Button rain = new Button(this); rain.setText("TIMEFALL"); Button cell = new Button(this); cell.setText("NEW CELL"); row.addView(rain); row.addView(cell); panel.addView(row);
        TextView help = new TextView(this); help.setTextColor(0xFFD7E2DA); help.setTextSize(12); help.setText("LEFT: walk   •   RIGHT: orbit   •   PINCH: zoom\nv0.2.1 crash-safe GLES2 renderer"); panel.addView(help);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT); pp.setMargins(16,16,0,0); root.addView(panel,pp);
        rain.setOnClickListener(v->{terrainView.r.raining=!terrainView.r.raining; updateStatus();});
        cell.setOnClickListener(v->{terrainView.r.seed++;terrainView.r.rebuild=true;updateStatus();});
        setContentView(root); updateStatus();
    }
    void updateStatus(){ if(status==null||terrainView==null)return; StrandRenderer r=terrainView.r; status.setText("STRAND TERRAIN v0.2.1\nCELL #"+r.seed+" • "+(r.raining?"TIMEFALL ACTIVE":"DRYING / DRY")+"\nporter • rocks • hydrology • wetness"); }
    @Override protected void onResume(){super.onResume();terrainView.onResume();}
    @Override protected void onPause(){terrainView.onPause();super.onPause();}

    class TerrainView extends GLSurfaceView {
        StrandRenderer r=new StrandRenderer(); float sx,sy,lx,ly,oldDist; boolean moving,pinch;
        TerrainView(){super(SafeActivity.this);setEGLContextClientVersion(2);setRenderer(r);setRenderMode(RENDERMODE_CONTINUOUSLY);}
        float clamp(float v){return Math.max(-1,Math.min(1,v));}
        float dist(MotionEvent e){float x=e.getX(0)-e.getX(1),y=e.getY(0)-e.getY(1);return (float)Math.sqrt(x*x+y*y);}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getPointerCount()>=2){float d=dist(e);if(oldDist>0)r.distance*=oldDist/d;r.distance=Math.max(18,Math.min(82,r.distance));oldDist=d;pinch=true;r.moveX=r.moveY=0;return true;}
            int a=e.getActionMasked(); if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){oldDist=0;pinch=false;moving=false;r.moveX=r.moveY=0;return true;}
            if(a==MotionEvent.ACTION_DOWN){sx=lx=e.getX();sy=ly=e.getY();moving=e.getX()<getWidth()*.44f;return true;}
            if(a==MotionEvent.ACTION_MOVE&&!pinch){if(moving){r.moveX=clamp((e.getX()-sx)/115f);r.moveY=clamp((e.getY()-sy)/115f);}else{float dx=e.getX()-lx,dy=e.getY()-ly;r.yaw+=dx*.24f;r.pitch=Math.max(20,Math.min(60,r.pitch+dy*.16f));lx=e.getX();ly=e.getY();}} return true;
        }
    }

    static class StrandRenderer implements GLSurfaceView.Renderer {
        static final int N=105; static final float CELL=128f;
        FloatBuffer terrainVB, rockVB, cubeVB; ShortBuffer terrainIB,cubeIB; int terrainCount,rockCount,cubeCount;
        int terrainProgram,objProgram; int taPos,taNormal,taMoist,tuMvp,tuWet,tuCam,oaPos,oaNormal,ouMvp,ouModel,ouColor,ouWet;
        int seed=1047; volatile boolean rebuild=true,raining=false; volatile float moveX,moveY; float wet,yaw=35,pitch=33,distance=46,px,pz,heading,moveAmount;
        float[][] heights; float[] proj=new float[16],view=new float[16],mvp=new float[16],model=new float[16]; Random rng; long last;
        boolean glReady=false;

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig cfg){
            GLES20.glClearColor(.055f,.075f,.065f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);GLES20.glEnable(GLES20.GL_CULL_FACE);
            try{
                terrainProgram=link(TVS,TFS); objProgram=link(OVS,OFS);
                if(terrainProgram==0||objProgram==0){glReady=false;return;}
                taPos=GLES20.glGetAttribLocation(terrainProgram,"aPos");taNormal=GLES20.glGetAttribLocation(terrainProgram,"aNormal");taMoist=GLES20.glGetAttribLocation(terrainProgram,"aMoist");tuMvp=GLES20.glGetUniformLocation(terrainProgram,"uMvp");tuWet=GLES20.glGetUniformLocation(terrainProgram,"uWet");tuCam=GLES20.glGetUniformLocation(terrainProgram,"uCam");
                oaPos=GLES20.glGetAttribLocation(objProgram,"aPos");oaNormal=GLES20.glGetAttribLocation(objProgram,"aNormal");ouMvp=GLES20.glGetUniformLocation(objProgram,"uMvp");ouModel=GLES20.glGetUniformLocation(objProgram,"uModel");ouColor=GLES20.glGetUniformLocation(objProgram,"uColor");ouWet=GLES20.glGetUniformLocation(objProgram,"uWet");
                buildCube();buildWorld();glReady=true;last=System.nanoTime();
            }catch(Throwable t){glReady=false;}
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(proj,0,52f,(float)w/Math.max(1,h),.35f,260f);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT); if(!glReady)return; if(rebuild)buildWorld();
            long now=System.nanoTime();float dt=last==0?.016f:Math.min(.045f,(now-last)/1e9f);last=now;float target=raining?1:0;wet+=(target-wet)*Math.min(1,dt*(raining?.62f:.10f));updatePlayer(dt);
            float ground=sampleHeight(px,pz),ty=ground+1.35f,yr=(float)Math.toRadians(yaw),pr=(float)Math.toRadians(pitch),cp=(float)Math.cos(pr);float cx=px+(float)Math.sin(yr)*cp*distance,cz=pz+(float)Math.cos(yr)*cp*distance,cy=ty+(float)Math.sin(pr)*distance+2.4f;
            GLES20.glClearColor(.045f+.015f*(1-wet),.060f+.020f*(1-wet),.055f+.012f*(1-wet),1);Matrix.setLookAtM(view,0,cx,cy,cz,px,ty,pz,0,1,0);Matrix.multiplyMM(mvp,0,proj,0,view,0);drawTerrain(cx,cy,cz);drawRocks();drawPlayer(ground,(float)(now/1e9));
        }
        void updatePlayer(float dt){float mx=moveX,f=-moveY,m=(float)Math.sqrt(mx*mx+f*f);moveAmount=Math.min(1,m);if(m>.08f){if(m>1){mx/=m;f/=m;}float yr=(float)Math.toRadians(yaw),fx=(float)Math.sin(yr),fz=(float)Math.cos(yr),rx=(float)Math.cos(yr),rz=-(float)Math.sin(yr);float vx=rx*mx+fx*f,vz=rz*mx+fz*f,s=5*(.35f+.65f*Math.min(1,m));px=Math.max(-57,Math.min(57,px+vx*s*dt));pz=Math.max(-57,Math.min(57,pz+vz*s*dt));heading=(float)Math.toDegrees(Math.atan2(vx,vz));}}
        void drawTerrain(float cx,float cy,float cz){GLES20.glUseProgram(terrainProgram);GLES20.glUniformMatrix4fv(tuMvp,1,false,mvp,0);GLES20.glUniform1f(tuWet,wet);GLES20.glUniform3f(tuCam,cx,cy,cz);terrainVB.position(0);GLES20.glEnableVertexAttribArray(taPos);GLES20.glVertexAttribPointer(taPos,3,GLES20.GL_FLOAT,false,28,terrainVB);terrainVB.position(3);GLES20.glEnableVertexAttribArray(taNormal);GLES20.glVertexAttribPointer(taNormal,3,GLES20.GL_FLOAT,false,28,terrainVB);terrainVB.position(6);GLES20.glEnableVertexAttribArray(taMoist);GLES20.glVertexAttribPointer(taMoist,1,GLES20.GL_FLOAT,false,28,terrainVB);GLES20.glDrawElements(GLES20.GL_TRIANGLES,terrainCount,GLES20.GL_UNSIGNED_SHORT,terrainIB);}
        void drawRocks(){if(rockVB==null)return;GLES20.glUseProgram(objProgram);GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0);GLES20.glUniform1f(ouWet,wet);Matrix.setIdentityM(model,0);GLES20.glUniformMatrix4fv(ouModel,1,false,model,0);GLES20.glUniform3f(ouColor,.105f,.115f,.108f);rockVB.position(0);GLES20.glEnableVertexAttribArray(oaPos);GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,rockVB);rockVB.position(3);GLES20.glEnableVertexAttribArray(oaNormal);GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,rockVB);GLES20.glDisable(GLES20.GL_CULL_FACE);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,rockCount);GLES20.glEnable(GLES20.GL_CULL_FACE);}
        void drawPlayer(float ground,float t){GLES20.glUseProgram(objProgram);GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0);GLES20.glUniform1f(ouWet,wet);cubeVB.position(0);GLES20.glEnableVertexAttribArray(oaPos);GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,cubeVB);cubeVB.position(3);GLES20.glEnableVertexAttribArray(oaNormal);GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,cubeVB);float gait=(float)Math.sin(t*9.5f)*.13f*moveAmount;part(0,1.25f,0,.52f,.75f,.30f,.075f,.105f,.105f,ground);part(0,2.03f,.01f,.30f,.30f,.30f,.24f,.20f,.16f,ground);part(0,1.32f,-.33f,.58f,.62f,.25f,.17f,.19f,.17f,ground);part(-.20f,.55f,gait,.17f,.62f,.18f,.055f,.075f,.078f,ground);part(.20f,.55f,-gait,.17f,.62f,.18f,.055f,.075f,.078f,ground);part(0,1.43f,-.55f,.46f,.42f,.20f,.24f,.27f,.22f,ground);}
        void part(float lx,float ly,float lz,float sx,float sy,float sz,float r,float g,float b,float ground){float hr=(float)Math.toRadians(heading),c=(float)Math.cos(hr),s=(float)Math.sin(hr),ox=lx*c+lz*s,oz=-lx*s+lz*c;Matrix.setIdentityM(model,0);Matrix.translateM(model,0,px+ox,ground+ly,pz+oz);Matrix.rotateM(model,0,heading,0,1,0);Matrix.scaleM(model,0,sx,sy,sz);GLES20.glUniformMatrix4fv(ouModel,1,false,model,0);GLES20.glUniform3f(ouColor,r,g,b);GLES20.glDrawElements(GLES20.GL_TRIANGLES,cubeCount,GLES20.GL_UNSIGNED_SHORT,cubeIB);}

        void buildWorld(){rebuild=false;rng=new Random(seed);px=pz=0;heights=new float[N][N];for(int z=0;z<N;z++)for(int x=0;x<N;x++){float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL;heights[z][x]=height(wx,wz);}ByteBuffer b=ByteBuffer.allocateDirect(N*N*28).order(ByteOrder.nativeOrder());terrainVB=b.asFloatBuffer();float step=CELL/(N-1);for(int z=0;z<N;z++)for(int x=0;x<N;x++){float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL,h=heights[z][x],hl=heights[z][Math.max(0,x-1)],hr=heights[z][Math.min(N-1,x+1)],hd=heights[Math.max(0,z-1)][x],hu=heights[Math.min(N-1,z+1)][x],nx=hl-hr,ny=2*step,nz=hd-hu,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);nx/=len;ny/=len;nz/=len;float lap=(hl+hr+hd+hu-4*h)/(step*step);terrainVB.put(wx).put(h).put(wz).put(nx).put(ny).put(nz).put(moisture(wx,wz,h,lap));}terrainVB.position(0);terrainCount=(N-1)*(N-1)*6;ByteBuffer ib=ByteBuffer.allocateDirect(terrainCount*2).order(ByteOrder.nativeOrder());terrainIB=ib.asShortBuffer();for(int z=0;z<N-1;z++)for(int x=0;x<N-1;x++){int i=z*N+x;terrainIB.put((short)i).put((short)(i+N)).put((short)(i+1)).put((short)(i+1)).put((short)(i+N)).put((short)(i+N+1));}terrainIB.position(0);buildRocks();}
        float height(float x,float z){float wx=(fbm(x*.012f+9,z*.012f-7,seed+41)-.5f)*18,wz=(fbm(x*.012f-4,z*.012f+5,seed+53)-.5f)*18,macro=(fbm((x+wx)*.018f,(z+wz)*.018f,seed)-.5f)*13,ridge=(1-Math.abs(noise((x+wx)*.047f+17,(z+wz)*.047f-9,seed+3)*2-1))*2.8f,humm=(fbm(x*.105f+8,z*.105f-4,seed+9)-.48f)*2,main=z+11+8*(float)Math.sin(x*.04f+seed*.0007f),val=-4.4f*(float)Math.exp(-(main*main)/(162f));return macro+ridge+humm+val;}
        float moisture(float x,float z,float h,float lap){float main=z+11+8*(float)Math.sin(x*.04f+seed*.0007f),m=.12f+.6f*(float)Math.exp(-(main*main)/128f);m+=Math.max(0,Math.min(.2f,(2-h)*.018f))+Math.max(0,Math.min(.18f,lap*.32f));return Math.max(0,Math.min(1,m));}
        float sampleHeight(float x,float z){if(heights==null)return 0;float gx=(x/CELL+.5f)*(N-1),gz=(z/CELL+.5f)*(N-1);int x0=Math.max(0,Math.min(N-2,(int)Math.floor(gx))),z0=Math.max(0,Math.min(N-2,(int)Math.floor(gz)));float tx=gx-x0,tz=gz-z0,a=heights[z0][x0]*(1-tx)+heights[z0][x0+1]*tx,bb=heights[z0+1][x0]*(1-tx)+heights[z0+1][x0+1]*tx;return a*(1-tz)+bb*tz;}
        void buildRocks(){int rocks=72;float[] out=new float[rocks*36*6];int[] p={0};for(int i=0;i<rocks;i++){float x=(rng.nextFloat()-.5f)*116,z=(rng.nextFloat()-.5f)*116,y=sampleHeight(x,z),rx=.35f+rng.nextFloat()*1.4f,rz=.35f+rng.nextFloat()*1.5f,ry=.3f+rng.nextFloat()*1.45f;if(rng.nextFloat()<.12f){rx*=1.8f;rz*=1.7f;ry*=1.5f;}addRock(out,p,x,y-ry*.18f,z,rx,ry,rz);}rockCount=p[0]/6;ByteBuffer b=ByteBuffer.allocateDirect(p[0]*4).order(ByteOrder.nativeOrder());rockVB=b.asFloatBuffer();rockVB.put(out,0,p[0]).position(0);}
        void addRock(float[] o,int[] p,float x,float y,float z,float rx,float ry,float rz){float[][] v={{-.65f,0,-.62f},{.63f,0,-.55f},{.58f,0,.65f},{-.60f,0,.58f},{-.40f,.72f,-.38f},{.42f,.83f,-.32f},{.36f,.68f,.42f},{-.37f,.77f,.35f}};for(int i=0;i<8;i++){v[i][0]=x+v[i][0]*rx;v[i][1]=y+v[i][1]*ry;v[i][2]=z+v[i][2]*rz;}int[][] q={{0,1,5},{0,5,4},{1,2,6},{1,6,5},{2,3,7},{2,7,6},{3,0,4},{3,4,7},{4,5,6},{4,6,7},{3,2,1},{3,1,0}};for(int[] t:q)addTri(o,p,v[t[0]],v[t[1]],v[t[2]]);}
        void addTri(float[] o,int[] p,float[] a,float[] b,float[] c){float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2],nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);if(l<.001f)l=1;nx/=l;ny/=l;nz/=l;putV(o,p,a,nx,ny,nz);putV(o,p,b,nx,ny,nz);putV(o,p,c,nx,ny,nz);}
        void putV(float[] o,int[] p,float[] v,float nx,float ny,float nz){int k=p[0];o[k]=v[0];o[k+1]=v[1];o[k+2]=v[2];o[k+3]=nx;o[k+4]=ny;o[k+5]=nz;p[0]+=6;}
        void buildCube(){float[] v={-.5f,-.5f,.5f,0,0,1,.5f,-.5f,.5f,0,0,1,.5f,.5f,.5f,0,0,1,-.5f,.5f,.5f,0,0,1,.5f,-.5f,-.5f,0,0,-1,-.5f,-.5f,-.5f,0,0,-1,-.5f,.5f,-.5f,0,0,-1,.5f,.5f,-.5f,0,0,-1,-.5f,-.5f,-.5f,-1,0,0,-.5f,-.5f,.5f,-1,0,0,-.5f,.5f,.5f,-1,0,0,-.5f,.5f,-.5f,-1,0,0,.5f,-.5f,.5f,1,0,0,.5f,-.5f,-.5f,1,0,0,.5f,.5f,-.5f,1,0,0,.5f,.5f,.5f,1,0,0,-.5f,.5f,.5f,0,1,0,.5f,.5f,.5f,0,1,0,.5f,.5f,-.5f,0,1,0,-.5f,.5f,-.5f,0,1,0,-.5f,-.5f,-.5f,0,-1,0,.5f,-.5f,-.5f,0,-1,0,.5f,-.5f,.5f,0,-1,0,-.5f,-.5f,.5f,0,-1,0};short[] idx={0,1,2,0,2,3,4,5,6,4,6,7,8,9,10,8,10,11,12,13,14,12,14,15,16,17,18,16,18,19,20,21,22,20,22,23};ByteBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());cubeVB=b.asFloatBuffer();cubeVB.put(v).position(0);cubeCount=idx.length;ByteBuffer ib=ByteBuffer.allocateDirect(idx.length*2).order(ByteOrder.nativeOrder());cubeIB=ib.asShortBuffer();cubeIB.put(idx).position(0);}
        float fbm(float x,float y,int s){float v=0,a=.5f,f=1;for(int i=0;i<5;i++){v+=a*noise(x*f,y*f,s+i*101);f*=2.03f;a*=.5f;}return v;}float noise(float x,float y,int s){int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float xf=x-xi,yf=y-yi,u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf),a=hash(xi,yi,s),b=hash(xi+1,yi,s),c=hash(xi,yi+1,s),d=hash(xi+1,yi+1,s);return lerp(lerp(a,b,u),lerp(c,d,u),v);}float hash(int x,int y,int s){int n=x*374761393+y*668265263+s*1442695041;n=(n^(n>>>13))*1274126177;return((n^(n>>>16))&0x7fffffff)/(float)0x7fffffff;}float lerp(float a,float b,float t){return a+(b-a)*t;}
        static int compile(int type,String src){int s=GLES20.glCreateShader(type);if(s==0)return 0;GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] ok={0};GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);return ok[0]==1?s:0;}static int link(String vs,String fs){int v=compile(GLES20.GL_VERTEX_SHADER,vs),f=compile(GLES20.GL_FRAGMENT_SHADER,fs);if(v==0||f==0)return 0;int p=GLES20.glCreateProgram();GLES20.glAttachShader(p,v);GLES20.glAttachShader(p,f);GLES20.glLinkProgram(p);int[] ok={0};GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0);return ok[0]==1?p:0;}
        static final String TVS="uniform mat4 uMvp;attribute vec3 aPos;attribute vec3 aNormal;attribute float aMoist;varying vec3 vPos;varying vec3 vN;varying float vM;void main(){vPos=aPos;vN=aNormal;vM=aMoist;gl_Position=uMvp*vec4(aPos,1.0);}";
        static final String TFS="precision mediump float;varying vec3 vPos;varying vec3 vN;varying float vM;uniform float uWet;uniform vec3 uCam;float h(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453);}void main(){vec3 N=normalize(vN);float slope=1.0-max(N.y,0.0);float a=h(floor(vPos.xz*.45));float b=h(floor(vPos.xz*.10+7.0));float rock=smoothstep(.24,.58,slope)+max(0.0,b-.72)*.55;rock=clamp(rock-vM*.12,0.0,1.0);float soil=(1.0-rock)*smoothstep(.52,.78,a)*.55;vec3 moss=vec3(.145,.205,.105)*(.78+.30*b);vec3 dirt=vec3(.125,.095,.060)*(.82+.24*a);vec3 basalt=vec3(.095,.108,.102)*(.78+.28*a);vec3 c=mix(moss,dirt,soil);c=mix(c,basalt,rock);float L=.30+.70*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);c*=L;float w=uWet*(.48+.52*vM);c*=1.0-w*.28;float fog=smoothstep(65.0,145.0,distance(uCam,vPos));gl_FragColor=vec4(mix(c,vec3(.13,.155,.145),fog),1.0);}";
        static final String OVS="uniform mat4 uMvp;uniform mat4 uModel;attribute vec3 aPos;attribute vec3 aNormal;varying vec3 vN;void main(){vN=aNormal;gl_Position=uMvp*uModel*vec4(aPos,1.0);}";
        static final String OFS="precision mediump float;varying vec3 vN;uniform vec3 uColor;uniform float uWet;void main(){vec3 N=normalize(vN);float l=.30+.70*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);gl_FragColor=vec4(uColor*l*(1.0-uWet*.18),1.0);}";
    }
}
