package com;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;


public class FileServer{
	private  String directory;
	private ServerSocket server;
	private int port;
	private boolean debugMode=false;
	
	public void setDebugMode(boolean debug){
		this.debugMode=debug;
	}
	public boolean getDebugMode(){
		return this.debugMode;
	}

	
	

	
	
	public void startServer() throws IOException{
		int clientId=0;
		if(debugMode){
			System.out.println("Server Started and waiting for clients to connect....");
		}
		while(true){
			try{
				 Socket client_socket=server.accept();
				 if(debugMode){
					 System.out.println("[CLIENT"+clientId+"]> Connection established with the client  at " + client_socket.getInetAddress() + ":" + client_socket.getPort());	 
				 }
				 RequestHandler rh=new RequestHandler(client_socket, this,clientId);
				 new Thread(rh).start();
				 clientId++;
                
			} catch(SocketException se){
				se.printStackTrace();
			}catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	public void makeServer(){
		try {
			if(debugMode){
				System.out.println("Creating Http File Server with: ");
				System.out.println("Port Number: "+this.port);
				System.out.println("Root Directory: "+this.getDirectory());
			}
			this.server=new ServerSocket(this.port);
			if(debugMode){
				System.out.println("Starting server..... ");
			}
			startServer();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public void stopServer(){
		try {
			server.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	public void setPort(int port){
		this.port=port;
	}
	public void setDirectory(String directory){
		this.directory=directory;
	}
	public String getDirectory(){
		return this.directory;
	}
}
