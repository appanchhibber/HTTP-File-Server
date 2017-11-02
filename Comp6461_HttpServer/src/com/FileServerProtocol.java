package com;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class FileServerProtocol {
	private String directory;
	private ServerSocket server;
	private int port;
	private Socket client_socket;
	private String request_line;
	private HashMap<String, String> request_headers;
	private HashMap<String,String> response_headers;
    private InputStreamReader isr = null;
    private BufferedReader reader = null;
    
    private String requested_uri;
    private String requested_method;
    private String file_path;
    private String query=null;
    private boolean isDirectory;
    private String recieved_body=null;
    private int status=0;

	public FileServerProtocol(){
		request_headers=new HashMap<String, String>();
		response_headers=new HashMap<String, String>();
		initalizeResponseHeader();
	}
	private void initalizeResponseHeader(){
		response_headers.put("Server","HttpFileServer v0.0");
		response_headers.put("Data", new Date().toString());
		response_headers.put("Connection","close");
	}

	public void processMethod() {
		String data=null;
		switch (requested_method) {
		case "GET":
			data=handleGet();
			break;
		case "POST":
		 data=handlePost();
			break;
		default:
			System.out.println("Method not supported");
			break;
		}
		writeResponse(data);
	}

	public String handlePost() {
		String result = "";
		BufferedWriter out = null;
		FileInputStream in = null;
		try {
			File outputFile = new File("." + file_path);
			out = new BufferedWriter(new FileWriter(outputFile, false));
			outputFile.createNewFile();
			if (query != null) {
				out.write(query);
				out.flush();
				out.close();
				outputFile = new File("." + file_path);
			}
			if (this.recieved_body != null) {
				out.write(recieved_body);
				out.flush();
				out.close();
				outputFile = new File("." + file_path);
			}
			in = new FileInputStream(outputFile);
			int c;
			while ((c = in.read()) != -1) {
				result += (char) c;
			}
			if (result.length() != 0) {
				status = 200;
			} else {
				status = 400;
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			status = 500;
			e.printStackTrace();
		} finally {
			try {
				if (out != null) {
					out.close();
				}
				if (in != null) {
					in.close();
				}

			} catch (IOException e) {
				status = 500;
				System.out.println("Error closing the buffer writer!!! "
						+ e.toString());
			}
		}

		return result.trim();
	}
		
	
	

	public String handleGet(){
		String result="";
		FileInputStream in=null;
		BufferedWriter out=null;
		try {
			File file_folder=isDirectory_File();
			if(isDirectory){
				for(File file:file_folder.listFiles()){
					result+=file.getName()+"\r\n";
				}
			}
			else{
				if(query!=null){
					out = new BufferedWriter(new FileWriter(file_folder, true));
					out.write(query);
					out.flush();
					file_folder=new File("."+this.file_path);
					out.close();
				}
				if(this.recieved_body!=null){
					out = new BufferedWriter(new FileWriter(file_folder, true));
					out.write(recieved_body);
					out.flush();
					file_folder=new File("."+this.file_path);
					out.close();
				}
				
				in = new FileInputStream(file_folder);
				int c;
					while ((c = in.read()) != -1) {
						result += (char) c;
					}
			
			}
			if(result.length()!=0){
				status=200;
			}
			else{
				status=400;
			}		 
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			status=404;
			e.printStackTrace();
		}catch (IOException e) {
			// TODO Auto-generated catch block
			status=500;
			e.printStackTrace();
		}finally{
			try {
				if(in!=null){
				in.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				status=500;
				e.printStackTrace();
			}
		}
		
		return result.trim();
	}
	public void writeResponse(String data){
        try {
    		
            String httpResponse = constructHttpHeader(status,data);
            		
            httpResponse+=data;
        	DataOutputStream out = new DataOutputStream(
        			client_socket.getOutputStream());	
        	out.write(httpResponse.getBytes());
			out.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public String constructHttpHeader(int status_code,String data){
		String header="HTTP/1.0 ";
		switch(status_code){
		case 200:
			header+="200 OK";
			break;
		case 400:
			header+="400 Bad Request";
			break;
		case 403:
			header+="403 Forbidden";
			break;
		case 404:
			header+="404 Not Found";
			break;
		case 501:
			header+="501 Not Implemented";
			break;
	     case 500:
	    	 header += "500 Internal Server Error";
	         break;
		}
		
		header+="\r\n"; //for other headers
		//For showing the header passed by client
		request_headers.remove("Content-Length");
		request_headers.remove("Content-Type");
		for(Map.Entry<String, String> request_head:request_headers.entrySet() ){
			if(response_headers.containsKey(request_head.getKey())){
				header+=request_head.getKey()+": "+request_head.getValue()+"\r\n";
				response_headers.remove(request_head.getKey());
			}else{
			header+=request_head.getKey()+": "+request_head.getValue()+"\r\n";}
		}
		//for showing headers  of server by default
		for(Map.Entry<String,String> response_head:response_headers.entrySet()){
			header+=response_head.getKey()+": "+response_head.getValue()+"\r\n";
		}
		header+="Content-Length: "+data.length()+"\r\n";
		header+="Content-Type: "+contentType(this.file_path);
		header+="\r\n\r\n";//for ending the http header
		return header;
	}
	
	
	public String prepareHttp_ResponseBody(){
		return null;
	}


	public void parseRequestLine(){
		String[] url_resource=this.request_line.split("\\s+");
		this.requested_method=url_resource[0].trim();
		this.requested_uri=url_resource[1].trim();
		 try {
			if (this.requested_uri.equals("/")) {
				this.file_path = this.directory;
			} else {
				if (!this.requested_uri.contains(this.directory)) {

					this.file_path = this.directory
							+ new URI(this.requested_uri).getPath();
				} else {
					this.file_path = new URI(this.requested_uri).getPath();
				}
			}
			 
			 //decide about the directory
			this.query=new URI(this.requested_uri).getQuery();
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	

	
	public File isDirectory_File(){
		File tempFile=new File("."+this.file_path);
		if(!tempFile.canRead()){
			tempFile.setReadable(true);
		}
		isDirectory=checkForDirectory(tempFile);
		return tempFile;
	}
	public String contentType(String path){
		String result="";
		if(path.contains(".")){
			path.split("\\.");
			result=Content_Type_Mapping.valueOf(path.split("\\.")[1]).getContent_Type();
		}
		return (result=="")?Content_Type_Mapping.default_content_type.getContent_Type():result;
	}
	
	public String cleanRequestedURI(){
		Stack<String> clean=new Stack<String>();
		String[] parts=this.requested_uri.split("/");
		for(String part:parts){
			if(part.isEmpty()|| part=="."){
				continue;
			}
			if(part==".."){
				clean.pop();
			}else{
				clean.push(part);	
			}
		}
		return clean.toString();
	}

	public void requestLines(String line){	
		String[] request=line.split("-1",2);
		request_line=request[0];
		String[] reqheaders=request[1].split("-1");
		for(String head:reqheaders){
			if(head.startsWith("/body")){
				this.recieved_body=head.replace("/body", "").trim();
				continue;
			}
			String[] headerkey_value=head.split(":",2);
			this.request_headers.put(headerkey_value[0].trim(),headerkey_value[1].trim());

		}
	}
	public void startServer() throws IOException{
		while(true){
			try( Socket socket=server.accept()){
				
				this.client_socket=socket;
				 isr =  new InputStreamReader(client_socket.getInputStream());
				 reader = new BufferedReader(isr,1024);
				 
				int length=0;
				String request_data="";
				String line;
				
		        while ((line=reader.readLine()) != null) {
		        	if(line.length()==0){
		        		break;
		        	}
					if (line.startsWith("Content-Length: ")) { // get the
						// content-length
						int index = line.indexOf(':') + 1;
						String len = line.substring(index).trim();
						length = Integer.parseInt(len);
					}
		        	request_data +=line+"-1";
		        }
		        String body="/body";
	            if (length > 0) {
	                int read;
	                while ((read = reader.read()) != -1) {
	                    body+=((char) read);
	                    if (body.length() == length+5){
	                        break;
	                    }
	                }
	                request_data+=body;
	            }
		        requestLines(request_data);
				parseRequestLine();
				processMethod();
                
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}finally{
			         // releases resources associated with the streams
			         if(isr!=null)
			            isr.close();
			         if(reader!=null)
			            reader.close();
			}
		}
	}

	public void makeServer(){
		try {
			this.server=new ServerSocket(this.port);
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
	
	public boolean checkForDirectory(File file){
		return file.isDirectory();
	}
	public boolean checkForFile(File file){
		return file.isFile();
	}
	private enum Content_Type_Mapping {
		html("text/html"), txt("text/plain"), png("image/png"), jpeg(
				"image/jpeg"), default_content_type("application/octet-stream");
		private final String content_type;

		private Content_Type_Mapping(String content_type) {
			this.content_type = content_type;
		}

		public String getContent_Type() {
			return this.content_type;
		}
	}
	public void setPort(int port){
		this.port=port;
	}
	public void setDirectory(String directory){
		this.directory=directory;
	}
}
