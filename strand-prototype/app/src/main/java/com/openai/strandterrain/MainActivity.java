package com.openai.strandterrain;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.opengl.*;
import android.view.*;
import android.widget.*;
import java.nio.*;
import java.util.*;

public class MainActivity extends Activity {
    TerrainView terrainView;
    TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        terrainView = new TerrainView();
        root.addView(terrainView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(26, 20, 26, 20);
        panel.setBackgroundColor(0x99040A07);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(15);
        panel.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        Button rain = new Button(this); rain.setText("TIMEFALL");
        Button regen = new Button(this); regen.setText("NEW CELL");
        buttons.addView(rain); buttons.addView(regen);
        panel.addView(buttons);

        TextView help = new TextView(this);
        help.setTextColor(0xFFD7E2DA); help.setTextSize(12);
        help.setText("LEFT SIDE: drag to walk   •   RIGHT SIDE: orbit\nPinch: zoom   •   Camera follows the porter");
        panel.addView(help);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        pp.setMargins(16, 16, 0, 0); root.addView(panel, pp);
        rain.setOnClickListener(v -> { terrainView.renderer.raining = !terrainView.renderer.raining; updateStatus(); });
        regen.setOnClickListener(v -> { terrainView.renderer.seed++; terrainView.renderer.rebuild = true; updateStatus(); });
        setContentView(root);
        updateStatus();
    }

    void updateStatus() {
        if (status == null || terrainView == null) return;
        TerrainRenderer r = terrainView.renderer;
        status.setText("STRAND TERRAIN v0.2\nCELL #" + r.seed + " • " +
                (r.raining ? "TIMEFALL ACTIVE" : "DRYING / DRY") + "\nProcedural geology • rocks • hydrology • porter");
    }

    @Override protected void onResume(){ super.onResume(); terrainView.onResume(); }
    @Override protected void onPause(){ terrainView.onPause(); super.onPause(); }

    class TerrainView extends GLSurfaceView {
        TerrainRenderer renderer = new TerrainRenderer();
        float lastX,lastY,startX,startY,oldDist; boolean moveMode=false,pinch=false;
        TerrainView(){ super(MainActivity.this); setEGLContextClientVersion(2); setRenderer(renderer); setRenderMode(RENDERMODE_CONTINUOUSLY); }
        float dist(MotionEvent e){ float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1); return (float)Math.sqrt(dx*dx+dy*dy); }
        float clamp(float v){ return Math.max(-1f,Math.min(1f,v)); }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getPointerCount()>=2){
                float d=dist(e); if(oldDist>0) renderer.distance*=oldDist/d;
                renderer.distance=Math.max(18,Math.min(82,renderer.distance)); oldDist=d; pinch=true;
                renderer.moveX=renderer.moveY=0; return true;
            }
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
                oldDist=0; pinch=false; moveMode=false; renderer.moveX=renderer.moveY=0; return true;
            }
            if(action==MotionEvent.ACTION_DOWN){
                lastX=startX=e.getX(); lastY=startY=e.getY(); moveMode=e.getX()<getWidth()*.44f; return true;
            }
            if(action==MotionEvent.ACTION_MOVE && !pinch){
                if(moveMode){
                    renderer.moveX=clamp((e.getX()-startX)/115f);
                    renderer.moveY=clamp((e.getY()-startY)/115f);
                } else {
                    float dx=e.getX()-lastX,dy=e.getY()-lastY;
                    renderer.yaw+=dx*.24f; renderer.pitch=Math.max(20,Math.min(60,renderer.pitch+dy*.16f));
                    lastX=e.getX(); lastY=e.getY();
                }
            }
            return true;
        }
    }

    static class TerrainRenderer implements GLSurfaceView.Renderer {
        static final int N=113;
        static final float CELL=128f;
        FloatBuffer terrainVB, rockVB, cubeVB, rainVB;
        ShortBuffer terrainIB, cubeIB;
        int terrainIndexCount,rockVertexCount,cubeIndexCount;
        int terrainProgram,objProgram,rainProgram;
        int taPos,taNormal,taMoist,tuMvp,tuWet,tuCam;
        int oaPos,oaNormal,ouMvp,ouModel,ouColor,ouWet;
        int raData,ruMvp,ruPlayer,ruTime,ruStrength;
        int seed=1047;
        volatile boolean rebuild=true;
        volatile boolean raining=false;
        volatile float moveX=0,moveY=0;
        float wet=0,yaw=35,pitch=33,distance=46;
        float playerX=0,playerZ=0,heading=0,moveAmount=0;
        float[][] heightGrid;
        float[] proj=new float[16],view=new float[16],mvp=new float[16],model=new float[16];
        Random rng;
        long lastNanos=0,startNanos=0;

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig cfg){
            GLES20.glClearColor(.055f,.075f,.065f,1); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE);
            terrainProgram=link(TERRAIN_VS,TERRAIN_FS);
            taPos=GLES20.glGetAttribLocation(terrainProgram,"aPos"); taNormal=GLES20.glGetAttribLocation(terrainProgram,"aNormal"); taMoist=GLES20.glGetAttribLocation(terrainProgram,"aMoist");
            tuMvp=GLES20.glGetUniformLocation(terrainProgram,"uMvp"); tuWet=GLES20.glGetUniformLocation(terrainProgram,"uWet"); tuCam=GLES20.glGetUniformLocation(terrainProgram,"uCam");
            objProgram=link(OBJ_VS,OBJ_FS);
            oaPos=GLES20.glGetAttribLocation(objProgram,"aPos"); oaNormal=GLES20.glGetAttribLocation(objProgram,"aNormal");
            ouMvp=GLES20.glGetUniformLocation(objProgram,"uMvp"); ouModel=GLES20.glGetUniformLocation(objProgram,"uModel"); ouColor=GLES20.glGetUniformLocation(objProgram,"uColor"); ouWet=GLES20.glGetUniformLocation(objProgram,"uWet");
            rainProgram=link(RAIN_VS,RAIN_FS);
            raData=GLES20.glGetAttribLocation(rainProgram,"aData"); ruMvp=GLES20.glGetUniformLocation(rainProgram,"uMvp"); ruPlayer=GLES20.glGetUniformLocation(rainProgram,"uPlayer"); ruTime=GLES20.glGetUniformLocation(rainProgram,"uTime"); ruStrength=GLES20.glGetUniformLocation(rainProgram,"uStrength");
            buildCube(); buildRain(); buildWorld();
            startNanos=lastNanos=System.nanoTime();
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
            GLES20.glViewport(0,0,w,h); Matrix.perspectiveM(proj,0,52f,(float)w/h,.35f,260f);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            if(rebuild) buildWorld();
            long now=System.nanoTime(); float dt=lastNanos==0?0.016f:Math.min(.045f,(now-lastNanos)/1_000_000_000f); lastNanos=now;
            float target=raining?1f:0f; float rate=raining?0.62f:0.10f; wet += (target-wet)*Math.min(1f,dt*rate);
            updatePlayer(dt);
            GLES20.glClearColor(.045f+.015f*(1-wet),.060f+.020f*(1-wet),.055f+.012f*(1-wet),1);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);

            float ground=sampleHeight(playerX,playerZ); float targetY=ground+1.35f;
            float yr=(float)Math.toRadians(yaw), pr=(float)Math.toRadians(pitch), cp=(float)Math.cos(pr);
            float cx=playerX+(float)(Math.sin(yr)*cp*distance), cz=playerZ+(float)(Math.cos(yr)*cp*distance), cy=targetY+(float)(Math.sin(pr)*distance)+2.4f;
            Matrix.setLookAtM(view,0,cx,cy,cz,playerX,targetY,playerZ,0,1,0); Matrix.multiplyMM(mvp,0,proj,0,view,0);

            drawTerrain(cx,cy,cz);
            drawRocks();
            drawPlayer((now-startNanos)/1_000_000_000f,ground);
            if(raining || wet>.18f) drawRain((now-startNanos)/1_000_000_000f,ground);
        }

        void updatePlayer(float dt){
            float mx=moveX, forward=-moveY; float mag=(float)Math.sqrt(mx*mx+forward*forward); moveAmount=Math.min(1f,mag);
            if(mag>.08f){
                mx/=Math.max(1f,mag); forward/=Math.max(1f,mag);
                float yr=(float)Math.toRadians(yaw);
                float fx=(float)Math.sin(yr), fz=(float)Math.cos(yr), rx=(float)Math.cos(yr), rz=-(float)Math.sin(yr);
                float vx=rx*mx+fx*forward, vz=rz*mx+fz*forward;
                float speed=5.0f*(.35f+.65f*Math.min(1f,mag));
                playerX+=vx*speed*dt; playerZ+=vz*speed*dt;
                playerX=Math.max(-57,Math.min(57,playerX)); playerZ=Math.max(-57,Math.min(57,playerZ));
                heading=(float)Math.toDegrees(Math.atan2(vx,vz));
            }
        }

        void drawTerrain(float cx,float cy,float cz){
            GLES20.glUseProgram(terrainProgram); GLES20.glUniformMatrix4fv(tuMvp,1,false,mvp,0); GLES20.glUniform1f(tuWet,wet); GLES20.glUniform3f(tuCam,cx,cy,cz);
            terrainVB.position(0); GLES20.glEnableVertexAttribArray(taPos); GLES20.glVertexAttribPointer(taPos,3,GLES20.GL_FLOAT,false,28,terrainVB);
            terrainVB.position(3); GLES20.glEnableVertexAttribArray(taNormal); GLES20.glVertexAttribPointer(taNormal,3,GLES20.GL_FLOAT,false,28,terrainVB);
            terrainVB.position(6); GLES20.glEnableVertexAttribArray(taMoist); GLES20.glVertexAttribPointer(taMoist,1,GLES20.GL_FLOAT,false,28,terrainVB);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,terrainIndexCount,GLES20.GL_UNSIGNED_SHORT,terrainIB);
        }

        void drawRocks(){
            if(rockVB==null || rockVertexCount==0) return;
            GLES20.glUseProgram(objProgram); GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0); GLES20.glUniform1f(ouWet,wet);
            Matrix.setIdentityM(model,0); GLES20.glUniformMatrix4fv(ouModel,1,false,model,0); GLES20.glUniform3f(ouColor,.115f,.124f,.115f);
            rockVB.position(0); GLES20.glEnableVertexAttribArray(oaPos); GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,rockVB);
            rockVB.position(3); GLES20.glEnableVertexAttribArray(oaNormal); GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,rockVB);
            GLES20.glDisable(GLES20.GL_CULL_FACE); GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,rockVertexCount); GLES20.glEnable(GLES20.GL_CULL_FACE);
        }

        void drawPlayer(float t,float ground){
            GLES20.glUseProgram(objProgram); GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0); GLES20.glUniform1f(ouWet,wet);
            cubeVB.position(0); GLES20.glEnableVertexAttribArray(oaPos); GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,cubeVB);
            cubeVB.position(3); GLES20.glEnableVertexAttribArray(oaNormal); GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,cubeVB);
            float gait=(float)Math.sin(t*9.5f)*.13f*moveAmount;
            part(0,1.25f,0,.52f,.75f,.30f,.075f,.105f,.105f,ground);
            part(0,2.03f,.01f,.30f,.30f,.30f,.24f,.20f,.16f,ground);
            part(0,1.32f,-.33f,.58f,.62f,.25f,.17f,.19f,.17f,ground);
            part(-.20f,.55f,gait,.17f,.62f,.18f,.055f,.075f,.078f,ground);
            part(.20f,.55f,-gait,.17f,.62f,.18f,.055f,.075f,.078f,ground);
            part(-.39f,1.34f,-gait*.45f,.14f,.58f,.14f,.075f,.10f,.105f,ground);
            part(.39f,1.34f,gait*.45f,.14f,.58f,.14f,.075f,.10f,.105f,ground);
            part(0,1.43f,-.56f,.46f,.42f,.20f,.24f,.27f,.22f,ground);
            part(0,1.78f,-.49f,.34f,.25f,.18f,.32f,.34f,.25f,ground);
        }

        void part(float lx,float ly,float lz,float sx,float sy,float sz,float r,float g,float b,float ground){
            float hr=(float)Math.toRadians(heading), c=(float)Math.cos(hr),s=(float)Math.sin(hr);
            float ox=lx*c+lz*s, oz=-lx*s+lz*c;
            Matrix.setIdentityM(model,0); Matrix.translateM(model,0,playerX+ox,ground+ly,playerZ+oz); Matrix.rotateM(model,0,heading,0,1,0); Matrix.scaleM(model,0,sx,sy,sz);
            GLES20.glUniformMatrix4fv(ouModel,1,false,model,0); GLES20.glUniform3f(ouColor,r,g,b); GLES20.glDrawElements(GLES20.GL_TRIANGLES,cubeIndexCount,GLES20.GL_UNSIGNED_SHORT,cubeIB);
        }

        void drawRain(float t,float ground){
            GLES20.glUseProgram(rainProgram); GLES20.glUniformMatrix4fv(ruMvp,1,false,mvp,0); GLES20.glUniform3f(ruPlayer,playerX,ground+1.2f,playerZ); GLES20.glUniform1f(ruTime,t); GLES20.glUniform1f(ruStrength,Math.max(wet,raining?.65f:0f));
            rainVB.position(0); GLES20.glEnableVertexAttribArray(raData); GLES20.glVertexAttribPointer(raData,4,GLES20.GL_FLOAT,false,16,rainVB);
            GLES20.glEnable(GLES20.GL_BLEND); GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA); GLES20.glDrawArrays(GLES20.GL_POINTS,0,420); GLES20.glDisable(GLES20.GL_BLEND);
        }

        void buildWorld(){
            rebuild=false; rng=new Random(seed); playerX=playerZ=0;
            heightGrid=new float[N][N];
            for(int z=0;z<N;z++) for(int x=0;x<N;x++){
                float wx=(x/(float)(N-1)-.5f)*CELL, wz=(z/(float)(N-1)-.5f)*CELL; heightGrid[z][x]=height(wx,wz);
            }
            ByteBuffer bb=ByteBuffer.allocateDirect(N*N*7*4).order(ByteOrder.nativeOrder()); terrainVB=bb.asFloatBuffer();
            float step=CELL/(N-1);
            for(int z=0;z<N;z++) for(int x=0;x<N;x++){
                float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL,h=heightGrid[z][x];
                float hl=heightGrid[z][Math.max(0,x-1)],hr=heightGrid[z][Math.min(N-1,x+1)],hd=heightGrid[Math.max(0,z-1)][x],hu=heightGrid[Math.min(N-1,z+1)][x];
                float nx=hl-hr,ny=2*step,nz=hd-hu,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); nx/=len;ny/=len;nz/=len;
                float lap=(hl+hr+hd+hu-4*h)/(step*step); float moist=moisture(wx,wz,h,lap);
                terrainVB.put(wx).put(h).put(wz).put(nx).put(ny).put(nz).put(moist);
            }
            terrainVB.position(0); terrainIndexCount=(N-1)*(N-1)*6; ByteBuffer bi=ByteBuffer.allocateDirect(terrainIndexCount*2).order(ByteOrder.nativeOrder()); terrainIB=bi.asShortBuffer();
            for(int z=0;z<N-1;z++) for(int x=0;x<N-1;x++){ int i=z*N+x; terrainIB.put((short)i).put((short)(i+N)).put((short)(i+1)); terrainIB.put((short)(i+1)).put((short)(i+N)).put((short)(i+N+1)); }
            terrainIB.position(0); buildRocks();
        }

        float height(float x,float z){
            float warpX=(fbm(x*.012f+9,z*.012f-7,seed+41)-.5f)*18f;
            float warpZ=(fbm(x*.012f-4,z*.012f+5,seed+53)-.5f)*18f;
            float macro=(fbm((x+warpX)*.018f,(z+warpZ)*.018f,seed)-.50f)*13.0f;
            float ridge=(1-Math.abs(noise((x+warpX)*.047f+17,(z+warpZ)*.047f-9,seed+3)*2-1))*2.8f;
            float humm=(fbm(x*.105f+8,z*.105f-4,seed+9)-.48f)*2.0f;
            float main=z+11f+8f*(float)Math.sin(x*.040f+seed*.0007f);
            float valley=-4.4f*(float)Math.exp(-(main*main)/(2*9.0f*9.0f));
            float trib=x-19f*(float)Math.sin(z*.030f+1.3f+seed*.0003f);
            float tributary=-1.6f*(float)Math.exp(-(trib*trib)/(2*5.2f*5.2f));
            return macro+ridge+humm+valley+tributary;
        }

        float moisture(float x,float z,float h,float lap){
            float main=z+11f+8f*(float)Math.sin(x*.040f+seed*.0007f);
            float trib=x-19f*(float)Math.sin(z*.030f+1.3f+seed*.0003f);
            float m=.12f+.55f*(float)Math.exp(-(main*main)/(2*8f*8f))+.22f*(float)Math.exp(-(trib*trib)/(2*5f*5f));
            m+=Math.max(0,Math.min(.20f,(2f-h)*.018f)); m+=Math.max(0,Math.min(.18f,lap*.32f));
            return Math.max(0,Math.min(1,m));
        }

        float sampleHeight(float x,float z){
            if(heightGrid==null) return 0;
            float gx=(x/CELL+.5f)*(N-1), gz=(z/CELL+.5f)*(N-1); int x0=Math.max(0,Math.min(N-2,(int)Math.floor(gx))), z0=Math.max(0,Math.min(N-2,(int)Math.floor(gz)));
            float tx=gx-x0,tz=gz-z0; float a=heightGrid[z0][x0]*(1-tx)+heightGrid[z0][x0+1]*tx; float b=heightGrid[z0+1][x0]*(1-tx)+heightGrid[z0+1][x0+1]*tx; return a*(1-tz)+b*tz;
        }

        void buildRocks(){
            int rocks=86; float[] out=new float[rocks*36*6]; int[] p={0};
            for(int i=0;i<rocks;i++){
                float x=(rng.nextFloat()-.5f)*116,z=(rng.nextFloat()-.5f)*116;
                float y=sampleHeight(x,z); float rx=.35f+rng.nextFloat()*1.45f,rz=.35f+rng.nextFloat()*1.55f,ry=.30f+rng.nextFloat()*1.55f;
                if(rng.nextFloat()<.13f){rx*=1.9f;rz*=1.8f;ry*=1.65f;}
                addRock(out,p,x,y-ry*.18f,z,rx,ry,rz,rng.nextFloat());
            }
            rockVertexCount=p[0]/6; ByteBuffer b=ByteBuffer.allocateDirect(p[0]*4).order(ByteOrder.nativeOrder()); rockVB=b.asFloatBuffer(); rockVB.put(out,0,p[0]).position(0);
        }

        void addRock(float[] o,int[] p,float x,float y,float z,float rx,float ry,float rz,float jitter){
            float[][] v={{-.65f,0,-.62f},{.63f,0,-.55f},{.58f,0,.65f},{-.60f,0,.58f},{-.40f,.72f,-.38f},{.42f,.83f,-.32f},{.36f,.68f,.42f},{-.37f,.77f,.35f}};
            for(int i=0;i<8;i++){ float j=1f+((i*37%7)-3)*.025f+jitter*.035f; v[i][0]=x+v[i][0]*rx*j;v[i][1]=y+v[i][1]*ry;v[i][2]=z+v[i][2]*rz*j; }
            int[][] t={{0,1,5},{0,5,4},{1,2,6},{1,6,5},{2,3,7},{2,7,6},{3,0,4},{3,4,7},{4,5,6},{4,6,7},{3,2,1},{3,1,0}};
            for(int[] q:t) addTri(o,p,v[q[0]],v[q[1]],v[q[2]]);
        }

        void addTri(float[] o,int[] p,float[] a,float[] b,float[] c){
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2], vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(l<.0001f)l=1;nx/=l;ny/=l;nz/=l;
            putV(o,p,a,nx,ny,nz);putV(o,p,b,nx,ny,nz);putV(o,p,c,nx,ny,nz);
        }
        void putV(float[] o,int[] p,float[] v,float nx,float ny,float nz){ int k=p[0];o[k]=v[0];o[k+1]=v[1];o[k+2]=v[2];o[k+3]=nx;o[k+4]=ny;o[k+5]=nz;p[0]+=6; }

        void buildCube(){
            float[] v={
                -.5f,-.5f,.5f,0,0,1, .5f,-.5f,.5f,0,0,1, .5f,.5f,.5f,0,0,1, -.5f,.5f,.5f,0,0,1,
                .5f,-.5f,-.5f,0,0,-1, -.5f,-.5f,-.5f,0,0,-1, -.5f,.5f,-.5f,0,0,-1, .5f,.5f,-.5f,0,0,-1,
                -.5f,-.5f,-.5f,-1,0,0, -.5f,-.5f,.5f,-1,0,0, -.5f,.5f,.5f,-1,0,0, -.5f,.5f,-.5f,-1,0,0,
                .5f,-.5f,.5f,1,0,0, .5f,-.5f,-.5f,1,0,0, .5f,.5f,-.5f,1,0,0, .5f,.5f,.5f,1,0,0,
                -.5f,.5f,.5f,0,1,0, .5f,.5f,.5f,0,1,0, .5f,.5f,-.5f,0,1,0, -.5f,.5f,-.5f,0,1,0,
                -.5f,-.5f,-.5f,0,-1,0, .5f,-.5f,-.5f,0,-1,0, .5f,-.5f,.5f,0,-1,0, -.5f,-.5f,.5f,0,-1,0};
            short[] idx={0,1,2,0,2,3,4,5,6,4,6,7,8,9,10,8,10,11,12,13,14,12,14,15,16,17,18,16,18,19,20,21,22,20,22,23};
            ByteBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder());cubeVB=b.asFloatBuffer();cubeVB.put(v).position(0); cubeIndexCount=idx.length;
            ByteBuffer ib=ByteBuffer.allocateDirect(idx.length*2).order(ByteOrder.nativeOrder());cubeIB=ib.asShortBuffer();cubeIB.put(idx).position(0);
        }

        void buildRain(){
            Random r=new Random(9917); ByteBuffer b=ByteBuffer.allocateDirect(420*4*4).order(ByteOrder.nativeOrder());rainVB=b.asFloatBuffer();
            for(int i=0;i<420;i++) rainVB.put((r.nextFloat()-.5f)*2).put((r.nextFloat()-.5f)*2).put(r.nextFloat()).put(r.nextFloat()); rainVB.position(0);
        }

        float fbm(float x,float y,int s){ float v=0,a=.5f,f=1; for(int i=0;i<5;i++){ v+=a*noise(x*f,y*f,s+i*101); f*=2.03f;a*=.5f;} return v; }
        float noise(float x,float y,int s){ int xi=(int)Math.floor(x), yi=(int)Math.floor(y); float xf=x-xi,yf=y-yi; float u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf); float a=hash(xi,yi,s),b=hash(xi+1,yi,s),c=hash(xi,yi+1,s),d=hash(xi+1,yi+1,s); return lerp(lerp(a,b,u),lerp(c,d,u),v); }
        float hash(int x,int y,int s){ int n=x*374761393+y*668265263+s*1442695041; n=(n^(n>>>13))*1274126177; return ((n^(n>>>16))&0x7fffffff)/(float)0x7fffffff; }
        float lerp(float a,float b,float t){ return a+(b-a)*t; }

        static int shader(int type,String src){ int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s); int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0); if(ok[0]==0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s)); return s; }
        static int link(String v,String f){ int p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,shader(GLES20.GL_VERTEX_SHADER,v)); GLES20.glAttachShader(p,shader(GLES20.GL_FRAGMENT_SHADER,f)); GLES20.glLinkProgram(p); int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0); if(ok[0]==0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p)); return p; }

        static final String TERRAIN_VS="uniform mat4 uMvp;attribute vec3 aPos;attribute vec3 aNormal;attribute float aMoist;varying vec3 vPos;varying vec3 vNormal;varying float vMoist;void main(){vPos=aPos;vNormal=normalize(aNormal);vMoist=aMoist;gl_Position=uMvp*vec4(aPos,1.0);}";
        static final String TERRAIN_FS=
                "precision mediump float;varying vec3 vPos;varying vec3 vNormal;varying float vMoist;uniform float uWet;uniform vec3 uCam;"+
                "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}"+
                "float n2(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}"+
                "float fbm(vec2 p){float v=0.0,a=.5;for(int i=0;i<4;i++){v+=a*n2(p);p*=2.03;a*=.5;}return v;}"+
                "void main(){vec3 N=normalize(vNormal);float slope=1.0-clamp(N.y,0.0,1.0);float macro=fbm(vPos.xz*.043);float mid=fbm(vPos.xz*.18+3.1);float micro=fbm(vPos.xz*.92-8.4);"+
                "float rock=clamp(smoothstep(.20,.55,slope)+(macro-.54)*.42+smoothstep(6.0,11.0,vPos.y)*.18-vMoist*.16,0.0,1.0);"+
                "float soil=clamp((1.0-rock)*(smoothstep(.40,.69,micro)*.58+smoothstep(.56,.76,mid)*.28)*(1.0-vMoist*.28),0.0,1.0);"+
                "vec3 moss=vec3(.145,.205,.105)*(0.70+macro*.43)+vec3(.035,.055,.022)*(micro-.5);vec3 dirt=vec3(.125,.095,.060)*(0.72+micro*.35);vec3 basalt=vec3(.095,.108,.102)*(0.72+micro*.40);"+
                "vec3 col=mix(moss,dirt,soil);col=mix(col,basalt,rock);col=mix(col,vec3(.105,.135,.092),vMoist*.20);float light=.28+.72*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);col*=light;"+
                "float localWet=uWet*(.48+.52*vMoist);col*=1.0-localWet*.29;float flat=1.0-smoothstep(.035,.16,slope);float puddle=smoothstep(.72,.94,vMoist)*flat*uWet;col=mix(col,vec3(.055,.075,.072),puddle*.62);"+
                "vec3 V=normalize(uCam-vPos),L=normalize(vec3(-.46,.83,.31)),H=normalize(V+L);float spec=pow(max(dot(N,H),0.0),mix(12.0,54.0,localWet));col+=vec3(.18,.20,.19)*spec*localWet*.38;"+
                "float d=distance(uCam,vPos);float fog=smoothstep(64.0,145.0,d);vec3 fogCol=mix(vec3(.16,.19,.17),vec3(.115,.135,.13),uWet);gl_FragColor=vec4(mix(col,fogCol,fog),1.0);}";
        static final String OBJ_VS="uniform mat4 uMvp;uniform mat4 uModel;attribute vec3 aPos;attribute vec3 aNormal;varying vec3 vN;varying vec3 vP;void main(){vec4 p=uModel*vec4(aPos,1.0);vP=p.xyz;vN=normalize(mat3(uModel)*aNormal);gl_Position=uMvp*p;}";
        static final String OBJ_FS="precision mediump float;varying vec3 vN;varying vec3 vP;uniform vec3 uColor;uniform float uWet;void main(){vec3 N=normalize(vN);float l=.26+.74*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);float grain=.91+.09*fract(sin(dot(vP.xz,vec2(18.21,53.77)))*2831.4);vec3 c=uColor*l*grain;c*=1.0-uWet*.19;gl_FragColor=vec4(c,1.0);}";
        static final String RAIN_VS="uniform mat4 uMvp;uniform vec3 uPlayer;uniform float uTime;attribute vec4 aData;varying float vA;void main(){float fall=mod(uTime*18.0+aData.z*23.0,23.0);vec3 p=uPlayer+vec3(aData.x*31.0,15.0-fall,aData.y*31.0);gl_Position=uMvp*vec4(p,1.0);gl_PointSize=1.7;vA=.32+.48*aData.w;}";
        static final String RAIN_FS="precision mediump float;uniform float uStrength;varying float vA;void main(){gl_FragColor=vec4(.68,.76,.72,vA*uStrength);}";
    }
}
