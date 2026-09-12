package com.openai.strandterrain;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLUtils;
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
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24,18,24,18);
        panel.setBackgroundColor(0x99040A07);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        panel.addView(status);

        LinearLayout row = new LinearLayout(this);
        Button rain = new Button(this); rain.setText("TIMEFALL");
        Button cell = new Button(this); cell.setText("NEW CELL");
        row.addView(rain); row.addView(cell); panel.addView(row);

        TextView help = new TextView(this);
        help.setTextColor(0xFFD7E2DA); help.setTextSize(12);
        help.setText("LEFT: walk   •   RIGHT: orbit   •   PINCH: zoom\nv0.3 scanned ground • geological rock clusters");
        panel.addView(help);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        pp.setMargins(16,16,0,0); root.addView(panel,pp);

        rain.setOnClickListener(v->{terrainView.r.raining=!terrainView.r.raining; updateStatus();});
        cell.setOnClickListener(v->{terrainView.r.seed++; terrainView.r.rebuild=true; updateStatus();});
        setContentView(root); updateStatus();
    }

    void updateStatus(){
        if(status==null||terrainView==null)return;
        StrandRenderer r=terrainView.r;
        status.setText("STRAND TERRAIN v0.3\nCELL #"+r.seed+" • "+(r.raining?"TIMEFALL ACTIVE":"DRYING / DRY")+"\nscanned moss/grass • soil • basalt • geological rocks");
    }
    @Override protected void onResume(){super.onResume();terrainView.onResume();}
    @Override protected void onPause(){terrainView.onPause();super.onPause();}

    class TerrainView extends GLSurfaceView {
        StrandRenderer r=new StrandRenderer();
        float sx,sy,lx,ly,oldDist; boolean moving,pinch;
        TerrainView(){super(SafeActivity.this);setEGLContextClientVersion(2);setRenderer(r);setRenderMode(RENDERMODE_CONTINUOUSLY);}
        float clamp(float v){return Math.max(-1,Math.min(1,v));}
        float dist(MotionEvent e){float x=e.getX(0)-e.getX(1),y=e.getY(0)-e.getY(1);return (float)Math.sqrt(x*x+y*y);}
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getPointerCount()>=2){float d=dist(e);if(oldDist>0)r.distance*=oldDist/d;r.distance=Math.max(18,Math.min(82,r.distance));oldDist=d;pinch=true;r.moveX=r.moveY=0;return true;}
            int a=e.getActionMasked();
            if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_CANCEL){oldDist=0;pinch=false;moving=false;r.moveX=r.moveY=0;return true;}
            if(a==MotionEvent.ACTION_DOWN){sx=lx=e.getX();sy=ly=e.getY();moving=e.getX()<getWidth()*.44f;return true;}
            if(a==MotionEvent.ACTION_MOVE&&!pinch){
                if(moving){r.moveX=clamp((e.getX()-sx)/115f);r.moveY=clamp((e.getY()-sy)/115f);}
                else{float dx=e.getX()-lx,dy=e.getY()-ly;r.yaw+=dx*.24f;r.pitch=Math.max(20,Math.min(60,r.pitch+dy*.16f));lx=e.getX();ly=e.getY();}
            }
            return true;
        }
    }

    class StrandRenderer implements GLSurfaceView.Renderer {
        static final int N=105;
        static final float CELL=128f;
        static final int MAX_ROCKS=150;

        FloatBuffer terrainVB,rockVB,cubeVB;
        ShortBuffer terrainIB,cubeIB;
        int terrainCount,rockCount,cubeCount;
        int terrainProgram,objProgram;
        int taPos,taNormal,taMoist,taRock,tuMvp,tuWet,tuCam,tuMoss,tuDirt,tuRockTex;
        int oaPos,oaNormal,ouMvp,ouModel,ouColor,ouWet,ouTextured,ouRockTex;
        int mossTex,dirtTex,rockTex;

        int seed=1047;
        volatile boolean rebuild=true,raining=false;
        volatile float moveX,moveY;
        float wet,yaw=35,pitch=33,distance=46,px,pz,heading,moveAmount;
        float[][] heights;
        float[] proj=new float[16],view=new float[16],mvp=new float[16],model=new float[16];
        Random rng;
        long last;
        boolean glReady=false;

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl,javax.microedition.khronos.egl.EGLConfig cfg){
            GLES20.glClearColor(.055f,.075f,.065f,1);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
            try{
                terrainProgram=link(TVS,TFS);
                objProgram=link(OVS,OFS);
                if(terrainProgram==0||objProgram==0){glReady=false;return;}

                taPos=GLES20.glGetAttribLocation(terrainProgram,"aPos");
                taNormal=GLES20.glGetAttribLocation(terrainProgram,"aNormal");
                taMoist=GLES20.glGetAttribLocation(terrainProgram,"aMoist");
                taRock=GLES20.glGetAttribLocation(terrainProgram,"aRock");
                tuMvp=GLES20.glGetUniformLocation(terrainProgram,"uMvp");
                tuWet=GLES20.glGetUniformLocation(terrainProgram,"uWet");
                tuCam=GLES20.glGetUniformLocation(terrainProgram,"uCam");
                tuMoss=GLES20.glGetUniformLocation(terrainProgram,"uMoss");
                tuDirt=GLES20.glGetUniformLocation(terrainProgram,"uDirt");
                tuRockTex=GLES20.glGetUniformLocation(terrainProgram,"uRockTex");

                oaPos=GLES20.glGetAttribLocation(objProgram,"aPos");
                oaNormal=GLES20.glGetAttribLocation(objProgram,"aNormal");
                ouMvp=GLES20.glGetUniformLocation(objProgram,"uMvp");
                ouModel=GLES20.glGetUniformLocation(objProgram,"uModel");
                ouColor=GLES20.glGetUniformLocation(objProgram,"uColor");
                ouWet=GLES20.glGetUniformLocation(objProgram,"uWet");
                ouTextured=GLES20.glGetUniformLocation(objProgram,"uTextured");
                ouRockTex=GLES20.glGetUniformLocation(objProgram,"uRockTex");

                mossTex=loadTexture(R.drawable.aerial_grass_rock_diff_1k);
                dirtTex=loadTexture(R.drawable.dirt_diff_1k);
                rockTex=loadTexture(R.drawable.rock_ground_diff_1k);
                buildCube();
                buildWorld();
                glReady=true;
                last=System.nanoTime();
            }catch(Throwable t){glReady=false;}
        }

        int loadTexture(int resId){
            BitmapFactory.Options o=new BitmapFactory.Options(); o.inScaled=false;
            Bitmap bmp=BitmapFactory.decodeResource(getResources(),resId,o);
            if(bmp==null)return 0;
            int[] id=new int[1]; GLES20.glGenTextures(1,id,0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,id[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,bmp,0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            bmp.recycle();
            return id[0];
        }

        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){
            GLES20.glViewport(0,0,w,h);
            Matrix.perspectiveM(proj,0,52f,(float)w/Math.max(1,h),.35f,260f);
        }

        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            if(!glReady)return;
            if(rebuild)buildWorld();
            long now=System.nanoTime();
            float dt=last==0?.016f:Math.min(.045f,(now-last)/1e9f); last=now;
            float target=raining?1:0;
            wet+=(target-wet)*Math.min(1,dt*(raining?.62f:.10f));
            updatePlayer(dt);

            float ground=sampleHeight(px,pz),ty=ground+1.35f;
            float yr=(float)Math.toRadians(yaw),pr=(float)Math.toRadians(pitch),cp=(float)Math.cos(pr);
            float cx=px+(float)Math.sin(yr)*cp*distance;
            float cz=pz+(float)Math.cos(yr)*cp*distance;
            float cy=ty+(float)Math.sin(pr)*distance+2.4f;
            GLES20.glClearColor(.045f+.015f*(1-wet),.060f+.020f*(1-wet),.055f+.012f*(1-wet),1);
            Matrix.setLookAtM(view,0,cx,cy,cz,px,ty,pz,0,1,0);
            Matrix.multiplyMM(mvp,0,proj,0,view,0);
            drawTerrain(cx,cy,cz);
            drawRocks();
            drawPlayer(ground,(float)(now/1e9));
        }

        void bindTexture(int unit,int tex){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0+unit);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,tex);
        }

        void updatePlayer(float dt){
            float mx=moveX,f=-moveY,m=(float)Math.sqrt(mx*mx+f*f); moveAmount=Math.min(1,m);
            if(m>.08f){
                if(m>1){mx/=m;f/=m;}
                float yr=(float)Math.toRadians(yaw),fx=(float)Math.sin(yr),fz=(float)Math.cos(yr),rx=(float)Math.cos(yr),rz=-(float)Math.sin(yr);
                float vx=rx*mx+fx*f,vz=rz*mx+fz*f,s=5*(.35f+.65f*Math.min(1,m));
                px=Math.max(-57,Math.min(57,px+vx*s*dt));
                pz=Math.max(-57,Math.min(57,pz+vz*s*dt));
                heading=(float)Math.toDegrees(Math.atan2(vx,vz));
            }
        }

        void drawTerrain(float cx,float cy,float cz){
            GLES20.glUseProgram(terrainProgram);
            GLES20.glUniformMatrix4fv(tuMvp,1,false,mvp,0);
            GLES20.glUniform1f(tuWet,wet);
            GLES20.glUniform3f(tuCam,cx,cy,cz);
            bindTexture(0,mossTex); bindTexture(1,dirtTex); bindTexture(2,rockTex);
            GLES20.glUniform1i(tuMoss,0); GLES20.glUniform1i(tuDirt,1); GLES20.glUniform1i(tuRockTex,2);
            terrainVB.position(0); GLES20.glEnableVertexAttribArray(taPos); GLES20.glVertexAttribPointer(taPos,3,GLES20.GL_FLOAT,false,32,terrainVB);
            terrainVB.position(3); GLES20.glEnableVertexAttribArray(taNormal); GLES20.glVertexAttribPointer(taNormal,3,GLES20.GL_FLOAT,false,32,terrainVB);
            terrainVB.position(6); GLES20.glEnableVertexAttribArray(taMoist); GLES20.glVertexAttribPointer(taMoist,1,GLES20.GL_FLOAT,false,32,terrainVB);
            terrainVB.position(7); GLES20.glEnableVertexAttribArray(taRock); GLES20.glVertexAttribPointer(taRock,1,GLES20.GL_FLOAT,false,32,terrainVB);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,terrainCount,GLES20.GL_UNSIGNED_SHORT,terrainIB);
        }

        void drawRocks(){
            if(rockVB==null)return;
            GLES20.glUseProgram(objProgram);
            GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0);
            GLES20.glUniform1f(ouWet,wet);
            GLES20.glUniform1f(ouTextured,1f);
            bindTexture(2,rockTex); GLES20.glUniform1i(ouRockTex,2);
            Matrix.setIdentityM(model,0); GLES20.glUniformMatrix4fv(ouModel,1,false,model,0);
            GLES20.glUniform3f(ouColor,.105f,.115f,.108f);
            rockVB.position(0); GLES20.glEnableVertexAttribArray(oaPos); GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,rockVB);
            rockVB.position(3); GLES20.glEnableVertexAttribArray(oaNormal); GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,rockVB);
            GLES20.glDisable(GLES20.GL_CULL_FACE);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,rockCount);
            GLES20.glEnable(GLES20.GL_CULL_FACE);
        }

        void drawPlayer(float ground,float t){
            GLES20.glUseProgram(objProgram);
            GLES20.glUniformMatrix4fv(ouMvp,1,false,mvp,0);
            GLES20.glUniform1f(ouWet,wet);
            GLES20.glUniform1f(ouTextured,0f);
            bindTexture(2,rockTex); GLES20.glUniform1i(ouRockTex,2);
            cubeVB.position(0); GLES20.glEnableVertexAttribArray(oaPos); GLES20.glVertexAttribPointer(oaPos,3,GLES20.GL_FLOAT,false,24,cubeVB);
            cubeVB.position(3); GLES20.glEnableVertexAttribArray(oaNormal); GLES20.glVertexAttribPointer(oaNormal,3,GLES20.GL_FLOAT,false,24,cubeVB);
            float gait=(float)Math.sin(t*9.5f)*.13f*moveAmount;
            part(0,1.25f,0,.52f,.75f,.30f,.075f,.105f,.105f,ground);
            part(0,2.03f,.01f,.30f,.30f,.30f,.24f,.20f,.16f,ground);
            part(0,1.32f,-.33f,.58f,.62f,.25f,.17f,.19f,.17f,ground);
            part(-.20f,.55f,gait,.17f,.62f,.18f,.055f,.075f,.078f,ground);
            part(.20f,.55f,-gait,.17f,.62f,.18f,.055f,.075f,.078f,ground);
            part(0,1.43f,-.55f,.46f,.42f,.20f,.24f,.27f,.22f,ground);
        }

        void part(float lx,float ly,float lz,float sx,float sy,float sz,float r,float g,float b,float ground){
            float hr=(float)Math.toRadians(heading),c=(float)Math.cos(hr),s=(float)Math.sin(hr),ox=lx*c+lz*s,oz=-lx*s+lz*c;
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,px+ox,ground+ly,pz+oz);
            Matrix.rotateM(model,0,heading,0,1,0);
            Matrix.scaleM(model,0,sx,sy,sz);
            GLES20.glUniformMatrix4fv(ouModel,1,false,model,0);
            GLES20.glUniform3f(ouColor,r,g,b);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,cubeCount,GLES20.GL_UNSIGNED_SHORT,cubeIB);
        }

        void buildWorld(){
            rebuild=false; rng=new Random(seed); px=pz=0;
            heights=new float[N][N];
            for(int z=0;z<N;z++)for(int x=0;x<N;x++){
                float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL;
                heights[z][x]=height(wx,wz);
            }

            ByteBuffer b=ByteBuffer.allocateDirect(N*N*32).order(ByteOrder.nativeOrder());
            terrainVB=b.asFloatBuffer(); float step=CELL/(N-1);
            for(int z=0;z<N;z++)for(int x=0;x<N;x++){
                float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL,h=heights[z][x];
                float hl=heights[z][Math.max(0,x-1)],hr=heights[z][Math.min(N-1,x+1)],hd=heights[Math.max(0,z-1)][x],hu=heights[Math.min(N-1,z+1)][x];
                float nx=hl-hr,ny=2*step,nz=hd-hu,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); nx/=len;ny/=len;nz/=len;
                float lap=(hl+hr+hd+hu-4*h)/(step*step);
                float moist=moisture(wx,wz,h,lap);
                float rock=rockScore(wx,wz,moist);
                terrainVB.put(wx).put(h).put(wz).put(nx).put(ny).put(nz).put(moist).put(rock);
            }
            terrainVB.position(0);

            terrainCount=(N-1)*(N-1)*6;
            ByteBuffer ib=ByteBuffer.allocateDirect(terrainCount*2).order(ByteOrder.nativeOrder()); terrainIB=ib.asShortBuffer();
            for(int z=0;z<N-1;z++)for(int x=0;x<N-1;x++){
                int i=z*N+x;
                terrainIB.put((short)i).put((short)(i+N)).put((short)(i+1));
                terrainIB.put((short)(i+1)).put((short)(i+N)).put((short)(i+N+1));
            }
            terrainIB.position(0);
            buildRocks();
        }

        float height(float x,float z){
            float wx=(fbm(x*.012f+9,z*.012f-7,seed+41)-.5f)*18;
            float wz=(fbm(x*.012f-4,z*.012f+5,seed+53)-.5f)*18;
            float macro=(fbm((x+wx)*.018f,(z+wz)*.018f,seed)-.5f)*12.5f;
            float ridge=(1-Math.abs(noise((x+wx)*.047f+17,(z+wz)*.047f-9,seed+3)*2-1))*2.4f;
            float humm=(fbm(x*.105f+8,z*.105f-4,seed+9)-.48f)*1.8f;
            float main=z+11+8*(float)Math.sin(x*.04f+seed*.0007f);
            float valley=-4.8f*(float)Math.exp(-(main*main)/(162f));
            float trib=x-20f*(float)Math.sin(z*.031f+1.3f+seed*.0003f);
            float tributary=-1.5f*(float)Math.exp(-(trib*trib)/(50f));
            return macro+ridge+humm+valley+tributary;
        }

        float moisture(float x,float z,float h,float lap){
            float main=z+11+8*(float)Math.sin(x*.04f+seed*.0007f);
            float trib=x-20f*(float)Math.sin(z*.031f+1.3f+seed*.0003f);
            float m=.10f+.58f*(float)Math.exp(-(main*main)/128f)+.20f*(float)Math.exp(-(trib*trib)/50f);
            m+=Math.max(0,Math.min(.18f,(2-h)*.018f));
            m+=Math.max(0,Math.min(.20f,lap*.34f));
            return clamp01(m);
        }

        float slopeAt(float x,float z){
            float e=.9f,dx=(sampleHeight(x+e,z)-sampleHeight(x-e,z))/(2*e),dz=(sampleHeight(x,z+e)-sampleHeight(x,z-e))/(2*e);
            return (float)Math.sqrt(dx*dx+dz*dz);
        }

        float convexAt(float x,float z){
            float e=2.0f,c=sampleHeight(x,z),avg=(sampleHeight(x+e,z)+sampleHeight(x-e,z)+sampleHeight(x,z+e)+sampleHeight(x,z-e))*.25f;
            return clamp01((c-avg)*.65f+.5f)-.5f;
        }

        float rockScore(float x,float z,float moist){
            float s=clamp01(slopeAt(x,z)/1.05f);
            float convex=Math.max(0,convexAt(x,z)*2f);
            float patch=fbm(x*.055f+12,z*.055f-5,seed+73);
            float open=(1-s)*(.55f+.45f*moist);
            float score=.48f*s+.28f*convex+.18f*(1-moist)+.20f*Math.max(0,patch-.55f)-.34f*open;
            return clamp01(score+.16f);
        }

        float sampleHeight(float x,float z){
            if(heights==null)return 0;
            float gx=(x/CELL+.5f)*(N-1),gz=(z/CELL+.5f)*(N-1);
            int x0=Math.max(0,Math.min(N-2,(int)Math.floor(gx))),z0=Math.max(0,Math.min(N-2,(int)Math.floor(gz)));
            float tx=gx-x0,tz=gz-z0;
            float a=heights[z0][x0]*(1-tx)+heights[z0][x0+1]*tx;
            float bb=heights[z0+1][x0]*(1-tx)+heights[z0+1][x0+1]*tx;
            return a*(1-tz)+bb*tz;
        }

        void buildRocks(){
            float[] out=new float[MAX_ROCKS*36*6]; int[] p={0}; int rocks=0;
            float[] ax=new float[14],az=new float[14]; int anchors=0;

            int desiredAnchors=6+rng.nextInt(5);
            for(int attempt=0;attempt<180 && anchors<desiredAnchors;attempt++){
                float x=(rng.nextFloat()-.5f)*112,z=(rng.nextFloat()-.5f)*112;
                float moist=moisture(x,z,sampleHeight(x,z),0),score=rockScore(x,z,moist);
                if(score<.38f+rng.nextFloat()*.18f)continue;
                boolean far=true; for(int j=0;j<anchors;j++){float dx=x-ax[j],dz=z-az[j];if(dx*dx+dz*dz<125){far=false;break;}}
                if(!far)continue;
                float rx=1.3f+rng.nextFloat()*2.7f,rz=1.1f+rng.nextFloat()*2.8f,ry=.8f+rng.nextFloat()*2.2f;
                addRock(out,p,x,sampleHeight(x,z)-ry*(.22f+rng.nextFloat()*.18f),z,rx,ry,rz,rng.nextFloat()*360f,rng.nextFloat());
                ax[anchors]=x;az[anchors]=z;anchors++;rocks++;
            }

            int clusters=5+rng.nextInt(5);
            for(int c=0;c<clusters && rocks<MAX_ROCKS-8;c++){
                float cx=0,cz=0; boolean ok=false;
                for(int attempt=0;attempt<50;attempt++){
                    cx=(rng.nextFloat()-.5f)*112;cz=(rng.nextFloat()-.5f)*112;
                    float m=moisture(cx,cz,sampleHeight(cx,cz),0),score=rockScore(cx,cz,m);
                    if(score>.23f+rng.nextFloat()*.20f){ok=true;break;}
                }
                if(!ok)continue;
                int members=2+rng.nextInt(5); float radius=2.0f+rng.nextFloat()*5.5f;
                float[] down=downhill(cx,cz); float baseHeading=(float)Math.toDegrees(Math.atan2(down[0],down[1]));
                for(int j=0;j<members && rocks<MAX_ROCKS;j++){
                    float ang=rng.nextFloat()*(float)Math.PI*2f;
                    float rad=radius*(float)Math.pow(rng.nextFloat(),1.8);
                    float x=cx+(float)Math.cos(ang)*rad,z=cz+(float)Math.sin(ang)*rad;
                    float rx=.45f+rng.nextFloat()*1.15f,rz=.42f+rng.nextFloat()*1.25f,ry=.35f+rng.nextFloat()*1.15f;
                    float burial=.18f+rng.nextFloat()*.20f;
                    addRock(out,p,x,sampleHeight(x,z)-ry*burial,z,rx,ry,rz,baseHeading+(rng.nextFloat()-.5f)*50f,rng.nextFloat());
                    rocks++;
                }
            }

            int screeZones=2+rng.nextInt(3);
            for(int s=0;s<screeZones && rocks<MAX_ROCKS-10;s++){
                float sx=0,sz=0; boolean ok=false;
                for(int a=0;a<60;a++){
                    sx=(rng.nextFloat()-.5f)*105;sz=(rng.nextFloat()-.5f)*105;
                    if(slopeAt(sx,sz)>.52f && rockScore(sx,sz,.2f)>.42f){ok=true;break;}
                }
                if(!ok)continue;
                float[] down=downhill(sx,sz); int n=6+rng.nextInt(7); float length=5+rng.nextFloat()*10;
                for(int j=0;j<n && rocks<MAX_ROCKS;j++){
                    float t=(j+rng.nextFloat())/n*length;
                    float side=(rng.nextFloat()-.5f)*3.5f;
                    float x=sx+down[0]*t-down[1]*side,z=sz+down[1]*t+down[0]*side;
                    float scale=.18f+rng.nextFloat()*.42f;
                    addRock(out,p,x,sampleHeight(x,z)-scale*.12f,z,scale*(.8f+rng.nextFloat()*.6f),scale*(.6f+rng.nextFloat()*.7f),scale*(.8f+rng.nextFloat()*.7f),rng.nextFloat()*360,rng.nextFloat());
                    rocks++;
                }
            }

            int embedded=14+rng.nextInt(13);
            for(int i=0;i<embedded && rocks<MAX_ROCKS;i++){
                for(int attempt=0;attempt<30;attempt++){
                    float x=(rng.nextFloat()-.5f)*112,z=(rng.nextFloat()-.5f)*112;
                    float m=moisture(x,z,sampleHeight(x,z),0),score=rockScore(x,z,m);
                    if(score<.46f+rng.nextFloat()*.22f)continue;
                    float sc=.28f+rng.nextFloat()*.65f;
                    addRock(out,p,x,sampleHeight(x,z)-sc*(.24f+rng.nextFloat()*.25f),z,sc*(.8f+rng.nextFloat()*.55f),sc*(.6f+rng.nextFloat()*.65f),sc*(.8f+rng.nextFloat()*.55f),rng.nextFloat()*360,rng.nextFloat());
                    rocks++;break;
                }
            }

            rockCount=p[0]/6;
            ByteBuffer b=ByteBuffer.allocateDirect(p[0]*4).order(ByteOrder.nativeOrder());
            rockVB=b.asFloatBuffer(); rockVB.put(out,0,p[0]).position(0);
        }

        float[] downhill(float x,float z){
            float e=1.5f,dx=sampleHeight(x+e,z)-sampleHeight(x-e,z),dz=sampleHeight(x,z+e)-sampleHeight(x,z-e);
            float vx=-dx,vz=-dz,l=(float)Math.sqrt(vx*vx+vz*vz); if(l<.001f)return new float[]{0,1};
            return new float[]{vx/l,vz/l};
        }

        void addRock(float[] o,int[] p,float x,float y,float z,float rx,float ry,float rz,float heading,float shape){
            if(p[0]+36*6>o.length)return;
            float[][] v={{-.68f,0,-.58f},{.61f,0,-.60f},{.58f,0,.67f},{-.62f,0,.55f},{-.43f,.70f,-.34f},{.39f,.84f,-.30f},{.34f,.67f,.43f},{-.36f,.76f,.37f}};
            float a=(float)Math.toRadians(heading),cs=(float)Math.cos(a),sn=(float)Math.sin(a);
            for(int i=0;i<8;i++){
                float jitter=1f+(((i*37)%7)-3)*.028f+(shape-.5f)*.10f;
                float lx=v[i][0]*rx*jitter,lz=v[i][2]*rz*jitter;
                v[i][0]=x+lx*cs-lz*sn; v[i][1]=y+v[i][1]*ry; v[i][2]=z+lx*sn+lz*cs;
            }
            int[][] q={{0,1,5},{0,5,4},{1,2,6},{1,6,5},{2,3,7},{2,7,6},{3,0,4},{3,4,7},{4,5,6},{4,6,7},{3,2,1},{3,1,0}};
            for(int[] t:q)addTri(o,p,v[t[0]],v[t[1]],v[t[2]]);
        }

        void addTri(float[] o,int[] p,float[] a,float[] b,float[] c){
            float ux=b[0]-a[0],uy=b[1]-a[1],uz=b[2]-a[2],vx=c[0]-a[0],vy=c[1]-a[1],vz=c[2]-a[2];
            float nx=uy*vz-uz*vy,ny=uz*vx-ux*vz,nz=ux*vy-uy*vx,l=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); if(l<.001f)l=1; nx/=l;ny/=l;nz/=l;
            putV(o,p,a,nx,ny,nz); putV(o,p,b,nx,ny,nz); putV(o,p,c,nx,ny,nz);
        }
        void putV(float[] o,int[] p,float[] v,float nx,float ny,float nz){int k=p[0];o[k]=v[0];o[k+1]=v[1];o[k+2]=v[2];o[k+3]=nx;o[k+4]=ny;o[k+5]=nz;p[0]+=6;}

        void buildCube(){
            float[] v={
                -.5f,-.5f,.5f,0,0,1, .5f,-.5f,.5f,0,0,1, .5f,.5f,.5f,0,0,1, -.5f,.5f,.5f,0,0,1,
                .5f,-.5f,-.5f,0,0,-1, -.5f,-.5f,-.5f,0,0,-1, -.5f,.5f,-.5f,0,0,-1, .5f,.5f,-.5f,0,0,-1,
                -.5f,-.5f,-.5f,-1,0,0, -.5f,-.5f,.5f,-1,0,0, -.5f,.5f,.5f,-1,0,0, -.5f,.5f,-.5f,-1,0,0,
                .5f,-.5f,.5f,1,0,0, .5f,-.5f,-.5f,1,0,0, .5f,.5f,-.5f,1,0,0, .5f,.5f,.5f,1,0,0,
                -.5f,.5f,.5f,0,1,0, .5f,.5f,.5f,0,1,0, .5f,.5f,-.5f,0,1,0, -.5f,.5f,-.5f,0,1,0,
                -.5f,-.5f,-.5f,0,-1,0, .5f,-.5f,-.5f,0,-1,0, .5f,-.5f,.5f,0,-1,0, -.5f,-.5f,.5f,0,-1,0};
            short[] idx={0,1,2,0,2,3,4,5,6,4,6,7,8,9,10,8,10,11,12,13,14,12,14,15,16,17,18,16,18,19,20,21,22,20,22,23};
            ByteBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()); cubeVB=b.asFloatBuffer(); cubeVB.put(v).position(0); cubeCount=idx.length;
            ByteBuffer ib=ByteBuffer.allocateDirect(idx.length*2).order(ByteOrder.nativeOrder()); cubeIB=ib.asShortBuffer(); cubeIB.put(idx).position(0);
        }

        float fbm(float x,float y,int s){float v=0,a=.5f,f=1;for(int i=0;i<5;i++){v+=a*noise(x*f,y*f,s+i*101);f*=2.03f;a*=.5f;}return v;}
        float noise(float x,float y,int s){int xi=(int)Math.floor(x),yi=(int)Math.floor(y);float xf=x-xi,yf=y-yi,u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf),a=hash(xi,yi,s),b=hash(xi+1,yi,s),c=hash(xi,yi+1,s),d=hash(xi+1,yi+1,s);return lerp(lerp(a,b,u),lerp(c,d,u),v);}
        float hash(int x,int y,int s){int n=x*374761393+y*668265263+s*1442695041;n=(n^(n>>>13))*1274126177;return ((n^(n>>>16))&0x7fffffff)/(float)0x7fffffff;}
        float lerp(float a,float b,float t){return a+(b-a)*t;}
        float clamp01(float v){return Math.max(0,Math.min(1,v));}

        int shader(int type,String src){
            int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s);
            int[] ok=new int[1]; GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0){GLES20.glDeleteShader(s);return 0;} return s;
        }
        int link(String vs,String fs){
            int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs); if(v==0||f==0)return 0;
            int p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,v); GLES20.glAttachShader(p,f); GLES20.glLinkProgram(p);
            int[] ok=new int[1]; GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0); GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
            if(ok[0]==0){GLES20.glDeleteProgram(p);return 0;} return p;
        }

        final String TVS="uniform mat4 uMvp;attribute vec3 aPos;attribute vec3 aNormal;attribute float aMoist;attribute float aRock;varying vec3 vPos;varying vec3 vN;varying float vMoist;varying float vRock;void main(){vPos=aPos;vN=aNormal;vMoist=aMoist;vRock=aRock;gl_Position=uMvp*vec4(aPos,1.0);}";
        final String TFS="precision mediump float;uniform sampler2D uMoss;uniform sampler2D uDirt;uniform sampler2D uRockTex;uniform float uWet;uniform vec3 uCam;varying vec3 vPos;varying vec3 vN;varying float vMoist;varying float vRock;void main(){vec3 N=normalize(vN);float slope=1.0-clamp(N.y,0.0,1.0);vec3 moss=texture2D(uMoss,vPos.xz/15.0).rgb;vec3 dirt=texture2D(uDirt,vPos.xz/2.7).rgb;vec3 rock=texture2D(uRockTex,vPos.xz/2.1).rgb;float rw=clamp(vRock+smoothstep(.24,.55,slope)*.62,0.0,1.0);float mw=clamp((.48+.62*vMoist)*(1.0-rw)*(1.0-smoothstep(.18,.50,slope)),0.0,1.0);float dw=clamp(1.0-rw-mw,0.0,1.0);float sum=max(.001,rw+mw+dw);rw/=sum;mw/=sum;dw/=sum;vec3 col=moss*mw+dirt*dw+rock*rw;float macro=.84+.20*texture2D(uMoss,vPos.xz/45.0).g;col*=macro;float light=.30+.70*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);col*=light;float localWet=uWet*(.42+.58*vMoist);col*=1.0-localWet*.27;vec3 V=normalize(uCam-vPos);vec3 H=normalize(V+normalize(vec3(-.46,.83,.31)));float spec=pow(max(dot(N,H),0.0),30.0)*localWet*.24;col+=vec3(spec);float d=distance(uCam,vPos);float fog=smoothstep(70.0,150.0,d);vec3 fogCol=mix(vec3(.16,.19,.17),vec3(.105,.125,.12),uWet);gl_FragColor=vec4(mix(col,fogCol,fog),1.0);}";
        final String OVS="uniform mat4 uMvp;uniform mat4 uModel;attribute vec3 aPos;attribute vec3 aNormal;varying vec3 vN;varying vec3 vP;void main(){vec4 p=uModel*vec4(aPos,1.0);vP=p.xyz;vN=normalize(mat3(uModel)*aNormal);gl_Position=uMvp*p;}";
        final String OFS="precision mediump float;uniform vec3 uColor;uniform float uWet;uniform float uTextured;uniform sampler2D uRockTex;varying vec3 vN;varying vec3 vP;void main(){vec3 N=normalize(vN);vec2 uv;if(abs(N.y)>.55)uv=vP.xz*.34;else if(abs(N.x)>abs(N.z))uv=vP.zy*.34;else uv=vP.xy*.34;vec3 tex=texture2D(uRockTex,uv).rgb;vec3 base=mix(uColor,tex,uTextured);float l=.27+.73*max(dot(N,normalize(vec3(-.46,.83,.31))),0.0);vec3 c=base*l;c*=1.0-uWet*.20*uTextured;gl_FragColor=vec4(c,1.0);}";
    }
}
