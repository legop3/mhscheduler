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
        panel.setPadding(28, 22, 28, 22);
        panel.setBackgroundColor(0x99050A07);
        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(16);
        status.setText("STRAND TERRAIN v0.1\n128 m deterministic cell • DRY");
        panel.addView(status);

        LinearLayout buttons = new LinearLayout(this);
        Button rain = new Button(this); rain.setText("TIMEFALL");
        Button regen = new Button(this); regen.setText("NEW CELL");
        buttons.addView(rain); buttons.addView(regen);
        panel.addView(buttons);
        TextView help = new TextView(this);
        help.setTextColor(0xFFD7E2DA); help.setTextSize(12);
        help.setText("Drag: orbit camera   •   Pinch: zoom\nSlope + height + deterministic noise drive moss / soil / basalt.");
        panel.addView(help);

        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.LEFT);
        pp.setMargins(18, 18, 0, 0); root.addView(panel, pp);
        rain.setOnClickListener(v -> { terrainView.renderer.wet = terrainView.renderer.wet < .5f ? 1f : 0f; updateStatus(); });
        regen.setOnClickListener(v -> { terrainView.renderer.seed++; terrainView.renderer.rebuild = true; updateStatus(); });
        setContentView(root);
    }

    void updateStatus() {
        status.setText("STRAND TERRAIN v0.1\n128 m deterministic cell #" + terrainView.renderer.seed +
                (terrainView.renderer.wet > .5f ? " • TIMEFALL SATURATED" : " • DRY"));
    }

    @Override protected void onResume(){ super.onResume(); terrainView.onResume(); }
    @Override protected void onPause(){ terrainView.onPause(); super.onPause(); }

    class TerrainView extends GLSurfaceView {
        TerrainRenderer renderer = new TerrainRenderer();
        float lastX,lastY,oldDist; boolean pinch;
        TerrainView(){ super(MainActivity.this); setEGLContextClientVersion(2); setRenderer(renderer); setRenderMode(RENDERMODE_CONTINUOUSLY); }
        float dist(MotionEvent e){ float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1); return (float)Math.sqrt(dx*dx+dy*dy); }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getPointerCount()>=2){ float d=dist(e); if(oldDist>0) renderer.distance*=oldDist/d; renderer.distance=Math.max(22,Math.min(105,renderer.distance)); oldDist=d; pinch=true; return true; }
            if(e.getActionMasked()==MotionEvent.ACTION_UP){ oldDist=0; pinch=false; return true; }
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){ lastX=e.getX(); lastY=e.getY(); return true; }
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE && !pinch){ float dx=e.getX()-lastX,dy=e.getY()-lastY; renderer.yaw+=dx*.28f; renderer.pitch=Math.max(18,Math.min(68,renderer.pitch+dy*.18f)); lastX=e.getX();lastY=e.getY(); }
            return true;
        }
    }

    static class TerrainRenderer implements GLSurfaceView.Renderer {
        static final int N=97; static final float CELL=128f;
        FloatBuffer vb; ShortBuffer ib; int indexCount,program; int seed=1047; volatile boolean rebuild=true; float wet=0,yaw=38,pitch=35,distance=64;
        int aPos,aNormal,uMvp,uWet,uCam; float[] proj=new float[16],view=new float[16],mvp=new float[16]; Random rng;

        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig cfg){
            GLES20.glClearColor(.055f,.075f,.065f,1); GLES20.glEnable(GLES20.GL_DEPTH_TEST); GLES20.glEnable(GLES20.GL_CULL_FACE);
            program=link(VS,FS); aPos=GLES20.glGetAttribLocation(program,"aPos"); aNormal=GLES20.glGetAttribLocation(program,"aNormal"); uMvp=GLES20.glGetUniformLocation(program,"uMvp"); uWet=GLES20.glGetUniformLocation(program,"uWet"); uCam=GLES20.glGetUniformLocation(program,"uCam");
            buildMesh();
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){ GLES20.glViewport(0,0,w,h); Matrix.perspectiveM(proj,0,50f,(float)w/h,.5f,260f); }
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){
            if(rebuild) buildMesh(); GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);
            float yr=(float)Math.toRadians(yaw), pr=(float)Math.toRadians(pitch); float cp=(float)Math.cos(pr);
            float cx=(float)(Math.sin(yr)*cp*distance), cz=(float)(Math.cos(yr)*cp*distance), cy=(float)(Math.sin(pr)*distance)+6;
            Matrix.setLookAtM(view,0,cx,cy,cz,0,2,0,0,1,0); Matrix.multiplyMM(mvp,0,proj,0,view,0);
            GLES20.glUseProgram(program); GLES20.glUniformMatrix4fv(uMvp,1,false,mvp,0); GLES20.glUniform1f(uWet,wet); GLES20.glUniform3f(uCam,cx,cy,cz);
            vb.position(0); GLES20.glEnableVertexAttribArray(aPos); GLES20.glVertexAttribPointer(aPos,3,GLES20.GL_FLOAT,false,24,vb);
            vb.position(3); GLES20.glEnableVertexAttribArray(aNormal); GLES20.glVertexAttribPointer(aNormal,3,GLES20.GL_FLOAT,false,24,vb);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,indexCount,GLES20.GL_UNSIGNED_SHORT,ib);
        }

        void buildMesh(){
            rebuild=false; rng=new Random(seed); float[][] h=new float[N][N];
            for(int z=0;z<N;z++) for(int x=0;x<N;x++){ float wx=(x/(float)(N-1)-.5f)*CELL, wz=(z/(float)(N-1)-.5f)*CELL; h[z][x]=height(wx,wz); }
            // deterministic embedded boulder mounds / rocky hummocks
            for(int b=0;b<24;b++){ float bx=(rng.nextFloat()-.5f)*110,bz=(rng.nextFloat()-.5f)*110,r=1.2f+rng.nextFloat()*3.5f,amp=.7f+rng.nextFloat()*2.4f;
                for(int z=0;z<N;z++) for(int x=0;x<N;x++){ float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL; float d=(float)Math.hypot(wx-bx,wz-bz)/r; if(d<1) h[z][x]+=amp*(1-d*d)*(1-d*d); }
            }
            ByteBuffer bb=ByteBuffer.allocateDirect(N*N*6*4).order(ByteOrder.nativeOrder()); vb=bb.asFloatBuffer();
            float step=CELL/(N-1);
            for(int z=0;z<N;z++) for(int x=0;x<N;x++){
                float wx=(x/(float)(N-1)-.5f)*CELL,wz=(z/(float)(N-1)-.5f)*CELL;
                float hl=h[z][Math.max(0,x-1)],hr=h[z][Math.min(N-1,x+1)],hd=h[Math.max(0,z-1)][x],hu=h[Math.min(N-1,z+1)][x];
                float nx=hl-hr,ny=2*step,nz=hd-hu,len=(float)Math.sqrt(nx*nx+ny*ny+nz*nz); nx/=len;ny/=len;nz/=len;
                vb.put(wx).put(h[z][x]).put(wz).put(nx).put(ny).put(nz);
            }
            vb.position(0); indexCount=(N-1)*(N-1)*6; ByteBuffer bi=ByteBuffer.allocateDirect(indexCount*2).order(ByteOrder.nativeOrder()); ib=bi.asShortBuffer();
            for(int z=0;z<N-1;z++) for(int x=0;x<N-1;x++){ int i=z*N+x; ib.put((short)i).put((short)(i+N)).put((short)(i+1)); ib.put((short)(i+1)).put((short)(i+N)).put((short)(i+N+1)); }
            ib.position(0);
        }

        float height(float x,float z){
            float macro=fbm(x*.018f,z*.018f,seed)*8f;
            float ridge=(1-Math.abs(noise(x*.045f+17,z*.045f-9,seed+3)*2-1))*3.2f;
            float humm=fbm(x*.11f+8,z*.11f-4,seed+9)*1.7f;
            float valley=-(float)Math.exp(-Math.pow((z + 12 + 8*Math.sin(x*.035))/8.5,2))*3.8f;
            return macro+ridge+humm+valley-4.5f;
        }
        float fbm(float x,float y,int s){ float v=0,a=.5f,f=1; for(int i=0;i<5;i++){ v+=a*noise(x*f,y*f,s+i*101); f*=2.03f;a*=.5f;} return v; }
        float noise(float x,float y,int s){ int xi=(int)Math.floor(x), yi=(int)Math.floor(y); float xf=x-xi,yf=y-yi; float u=xf*xf*(3-2*xf),v=yf*yf*(3-2*yf); float a=hash(xi,yi,s),b=hash(xi+1,yi,s),c=hash(xi,yi+1,s),d=hash(xi+1,yi+1,s); return lerp(lerp(a,b,u),lerp(c,d,u),v); }
        float hash(int x,int y,int s){ int n=x*374761393+y*668265263+s*1442695041; n=(n^(n>>>13))*1274126177; return ((n^(n>>>16))&0x7fffffff)/(float)0x7fffffff; }
        float lerp(float a,float b,float t){ return a+(b-a)*t; }

        static int shader(int type,String src){ int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,src); GLES20.glCompileShader(s); int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0); if(ok[0]==0) throw new RuntimeException(GLES20.glGetShaderInfoLog(s)); return s; }
        static int link(String v,String f){ int p=GLES20.glCreateProgram(); GLES20.glAttachShader(p,shader(GLES20.GL_VERTEX_SHADER,v)); GLES20.glAttachShader(p,shader(GLES20.GL_FRAGMENT_SHADER,f)); GLES20.glLinkProgram(p); int[] ok=new int[1];GLES20.glGetProgramiv(p,GLES20.GL_LINK_STATUS,ok,0); if(ok[0]==0) throw new RuntimeException(GLES20.glGetProgramInfoLog(p)); return p; }

        static final String VS =
                "uniform mat4 uMvp; attribute vec3 aPos; attribute vec3 aNormal; varying vec3 vPos; varying vec3 vNormal; void main(){vPos=aPos;vNormal=normalize(aNormal);gl_Position=uMvp*vec4(aPos,1.0);}";
        static final String FS =
                "precision mediump float; varying vec3 vPos; varying vec3 vNormal; uniform float uWet; uniform vec3 uCam;"+
                "float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}"+
                "float n2(vec2 p){vec2 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);return mix(mix(hash(i),hash(i+vec2(1,0)),f.x),mix(hash(i+vec2(0,1)),hash(i+vec2(1,1)),f.x),f.y);}"+
                "float fbm(vec2 p){float v=0.0,a=.5;for(int i=0;i<4;i++){v+=a*n2(p);p*=2.03;a*=.5;}return v;}"+
                "void main(){vec3 N=normalize(vNormal);float slope=1.0-clamp(N.y,0.0,1.0);float macro=fbm(vPos.xz*.045);float micro=fbm(vPos.xz*.65);"+
                "float rock=clamp(smoothstep(.22,.62,slope)+smoothstep(5.0,12.0,vPos.y)*.28+(macro-.55)*.35,0.0,1.0);"+
                "float soil=clamp((1.0-rock)*smoothstep(.36,.72,micro)*(.75+.25*smoothstep(-7.0,0.0,vPos.y)),0.0,1.0);"+
                "vec3 moss=vec3(.17,.225,.125)*(0.72+macro*.42)+vec3(.035,.06,.025)*(micro-.5);"+
                "vec3 dirt=vec3(.145,.115,.075)*(0.72+micro*.38);vec3 basalt=vec3(.115,.125,.116)*(0.70+micro*.42);"+
                "vec3 col=mix(moss,dirt,soil);col=mix(col,basalt,rock);float light=.30+.70*max(dot(N,normalize(vec3(-.45,.82,.34))),0.0);col*=light;"+
                "col*=mix(1.0,.72,uWet);float wetSheen=uWet*pow(max(dot(reflect(normalize(vPos-uCam),N),normalize(vec3(-.45,.82,.34))),0.0),18.0);col+=wetSheen*.14;"+
                "float d=distance(uCam,vPos);float fog=smoothstep(72.0,150.0,d);vec3 fogCol=vec3(.16,.19,.17);gl_FragColor=vec4(mix(col,fogCol,fog),1.0);}";
    }
}
