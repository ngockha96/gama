package ummisco.gama.modernOpenGL;
 
import java.nio.ByteBuffer;

import com.jogamp.opengl.GL2;
 
public class FrameBufferObject {
 
    protected static final int REFLECTION_WIDTH = 1280;//320;
    private static final int REFLECTION_HEIGHT = 720;//180;
    
    private int frameBufferID;
    private int depthBufferID;
    private int depthBufferTextureID;
    private int textureID;
    
    private int[] frameBufferArray;
    private int[] depthBufferArray;
    private int[] depthBufferTextureArray;
    private int[] textureArray;
    
    private GL2 gl;
 
    public FrameBufferObject(GL2 gl) {//call when loading the game
    	this.gl = gl;
        initialiseFrameBuffer();
    }
 
    public void cleanUp() {//call when closing the game
        //gl.glDeleteFramebuffers(frameBufferID,frameBufferArray,0);
        gl.glDeleteTextures(textureID,textureArray,0);
        //gl.glDeleteRenderbuffers(depthBufferID,depthBufferArray,0);
    }
 
    public void bindFrameBuffer() {//call before rendering to this FBO
        bindFrameBuffer(frameBufferID,REFLECTION_WIDTH,REFLECTION_HEIGHT);
    }
     
    public void unbindCurrentFrameBuffer() {//call to switch to default frame buffer
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
        gl.glViewport(0, 0, 1000, 800);
    }
 
    public int getFBOTexture() {//get the resulting texture
        return textureID;
    }
     
    public int getDepthTexture(){//get the resulting depth texture
        return depthBufferTextureID;
    }
 
    private void initialiseFrameBuffer() {
        createFrameBuffer();
        createTextureAttachment(REFLECTION_WIDTH,REFLECTION_HEIGHT);
        createDepthBufferAttachment(REFLECTION_WIDTH,REFLECTION_HEIGHT);
        unbindCurrentFrameBuffer();
    }
     
    private void bindFrameBuffer(int frameBuffer, int width, int height){
        gl.glBindTexture(GL2.GL_TEXTURE_2D, 0);//To make sure the texture isn't bound
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, frameBuffer);
        gl.glViewport(0, 0, width, height);
    }
 
    private int createFrameBuffer() {
    	frameBufferArray = new int[1];
    	frameBufferID = 1;
    	gl.glGenFramebuffers(frameBufferID,frameBufferArray,0);
        //generate name for frame buffer
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, frameBufferID);
        //create the framebuffer
        gl.glDrawBuffer(GL2.GL_COLOR_ATTACHMENT0);
        //indicate that we will always render to color attachment 0
        return frameBufferID;
    }
 
    private int createTextureAttachment(int width, int height) {
    	
		textureArray = new int[1];
		textureID = 1;
        gl.glGenTextures(textureID,textureArray,0);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, textureID);
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGB, width, height,
                0, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        gl.glFramebufferTextureEXT(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0,
                textureID, 0);
        return textureID;
    }
     
    private int createDepthTextureAttachment(int width, int height){
    	depthBufferTextureArray = new int[2];
		depthBufferTextureID = 2;
		gl.glGenTextures(depthBufferTextureID,depthBufferTextureArray,0);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, depthBufferTextureID);
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_DEPTH_COMPONENT32, width, height,
                0, GL2.GL_DEPTH_COMPONENT, GL2.GL_FLOAT, (ByteBuffer) null);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        gl.glFramebufferTextureEXT(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT,
        		depthBufferTextureID, 0);
        return depthBufferTextureID;
    }
 
    private int createDepthBufferAttachment(int width, int height) {
    	depthBufferArray = new int[1];
    	depthBufferID = 3;
    	gl.glGenRenderbuffers(depthBufferID, depthBufferArray, 0);
        gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, depthBufferID);
        gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT, width,
                height);
        gl.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT,
        		GL2.GL_RENDERBUFFER, depthBufferID);
        return depthBufferID;
    }
 
}