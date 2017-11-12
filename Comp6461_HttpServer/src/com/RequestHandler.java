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
import java.net.Socket;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import test.hello.JSONTemplate;

import com.google.gson.Gson;

public class RequestHandler implements Runnable {
	
	public RequestHandler(Socket client_socket, FileServer fileServer,
			int clientId) {
		this.client_socket = client_socket;
		this.clientId = clientId;
		this.fileServer = fileServer;
		requestHeaders = new HashMap<String, String>();
	}
	private void initalizeResponseHeader() {
		response_headers.put("Host", "");
		response_headers.put("Content-Length", "");
		response_headers.put("Server", "HttpFileServer v0.0");
		response_headers.put("Connection", "close");
		response_headers.put("Content-Disposition", "inline");
		response_headers.put("Data", new Date().toString());
	}
	
	public void processMethod() {
		switch (requestMethod) {
		case "GET":
			responseBody = handleGet();
			break;
		case "POST":
			responseBody = handlePost();
			break;
		default:
			System.out.println("Method not supported");
			break;
		}

			writeResponse();
		
	}
	
	@Override
	public void run() {
		try {
			synchronized (this) {
				this.response_headers = new HashMap<String, String>();
				if (fileServer.getDebugMode()) {
					System.out.println("Request Send by client number: "
							+ clientId);
					System.out
							.println("Reading the request sent by client number: "
									+ clientId);
				}
				initalizeResponseHeader();
				intializeClientIOStream();
				String request = readRequest();
				parseRequest(request);
				processMethod();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}



	public void intializeClientIOStream() {
		try {
			socketInStream = new BufferedReader(new InputStreamReader(
					client_socket.getInputStream()));
			socketOutStream = new DataOutputStream(
					client_socket.getOutputStream());
		} catch (IOException e) {
			System.out.println("error opening output or input Stream");
			e.printStackTrace();
		}
	}

	public String constructHttpHeader() {
		String header = "HTTP/1.0 ";
		switch (this.responseStatus) {
		case 200:
			header += "200 OK";
			break;
		case 400:
			header += "400 Bad Request";
			break;
		case 403:
			header += "403 Forbidden";
			break;
		case 404:
			header += "404 Not Found";
			break;
		case 501:
			header += "501 Not Implemented";
			break;
		case 500:
			header += "500 Internal Server Error";
			break;
		}

		header += "\r\n"; // for other headers
		// For showing the header passed by client
		header += mergeRequest_ResponseHeader();
		return header;
	}

	public String mergeRequest_ResponseHeader(){			
			HashMap<String, String> newResponseHeader=new HashMap<String, String>(response_headers);
			newResponseHeader.putAll(requestHeaders);
			String header="";
			for(Map.Entry<String, String> head:newResponseHeader.entrySet()){
				if(head.getKey().equals("Content-Length")){
					header+=head.getKey()+":\r\n";
				}else{
					header+=head.getKey()+": "+head.getValue()+"\r\n";
				}
			}
			return header;
	}



	public String cleanRequestedURI(String uri) {
		Stack<String> clean = new Stack<String>();
		String[] parts = uri.split("/");
		for (String part : parts) {
			if (part.isEmpty() || part == ".") {
				continue;
			}
			if (part == "..") {
				clean.pop();
			} else {
				clean.push(part);
			}
		}
		return clean.toString();
	}

	public String createFilePath(String uri) {
		String result = "";
		// this.requestURI=cleanRequestedURI(url_resource[1].trim());
		result = uri;
		// for separating query parameters from URI
		if (uri.contains("?")) {
			this.queryParameters = result.split("\\?")[1];
			result = result.split("\\?")[0];
		}
		// if URI doesn't start with '/'
		if (result.indexOf("/") != 0) {
			result = "/" + result;
		}
		// for setting default path of the URI
		if (result.equals("/")) {
			result = result + fileServer.getDirectory();
		}
		if (!result.contains(fileServer.getDirectory())) {
			result = fileServer.getDirectory() + result;
		}
		return result.trim();
	}

	public void parseRequest(String line) {
		String[] request = line.split("-1", 2);
		
		// for the Status line
		String[] url_resource = request[0].split("\\s+");
		if (fileServer.getDebugMode()) {
			System.out.println("Request by Client: ");
			System.out.println(request[0]);
		}
		if(url_resource.length>1){
		this.requestMethod = url_resource[0].trim();
		clientURI=url_resource[1].trim();

		this.requestURI = createFilePath(clientURI);
		
		// for request headers
		String[] reqheaders = request[1].split("-1");
		for (String head : reqheaders) {
			if (head.startsWith("/body")) {
				this.requestBody = head.replace("/body", "").trim();
				if (fileServer.getDebugMode()) {
					System.out.println(requestBody);
				}
				continue;
			}
			if (fileServer.getDebugMode()) {
				System.out.println(head);
			}
			String[] headerkey_value = head.split(":", 2);
			this.requestHeaders.put(headerkey_value[0].trim(),
					headerkey_value[1].trim());
		}

		}else{
			responseStatus=400;
			writeResponse();
		}

	}

	public String readRequest() {
		int length = 0;
		String request_data = "";
		String line;
		try {

			while ((line = socketInStream.readLine()) != null) {
				if (line.length() == 0) {
					break;
				}
				if (line.startsWith("Content-Length: ")) { // get the
					// content-length
					int index = line.indexOf(':') + 1;
					String len = line.substring(index).trim();
					length = Integer.parseInt(len);
				}
				request_data += line + "-1";
			}
			String body = "/body";
			if (length > 0) {
				int read;
				while ((read = socketInStream.read()) != -1) {
					body += ((char) read);
					if (body.length() == length + 5) {
						break;
					}
				}
				request_data += body;
			}
		} catch (IOException e) {
			System.out.println("Error Reading the request");
			e.printStackTrace();
		}
		return request_data;
	}



	public void writeResponse() {
		try {
			if (fileServer.getDebugMode()) {
				System.out.println("Writing the response to the client: "
						+ clientId);
			}
			String httpResponse = constructHttpHeader();
			if(responseStatus==200){
				String result="";
				if(!requestHeaders.containsKey("Content-Type"))
				{
					requestHeaders.put("Content-Type", contentType(this.requestURI));
					httpResponse+="Content-Type: "+requestHeaders.get("Content-Type")+"\r\n";
				}
				httpResponse+="\r\n";// for ending the http header section
			switch(requestHeaders.get("Content-Type")){
			case "text/plain": 
				result= responseBody;
				break;
			case "application/json":
				JSONTemplate temp=new JSONTemplate();
				if(clientURI.contains("?")){
					temp.setDirectory(this.clientURI.split("\\?")[0]);
				}else{
				temp.setDirectory(this.clientURI);
				}
				temp.setContent(responseBody);
				result= new Gson().toJson(temp);
				break;
			case "application/xml":
				break;
			case "text/html":
				result= handleHtmlResponse();
				break;
			default: 
				result= responseBody;
				break;
			}
				
				httpResponse=httpResponse.replace("Content-Length:", "Content-Length: "+result.getBytes().length);
				httpResponse+=result.trim();
			}
			if (fileServer.getDebugMode()) {
				System.out.println("Response message: ");
				System.out.println(httpResponse);
			}
			socketOutStream = new DataOutputStream(
					client_socket.getOutputStream());
			socketOutStream.write(httpResponse.getBytes());
			socketOutStream.flush();
			if (fileServer.getDebugMode()) {
				System.out.println("Message Sent");
				System.out.println("Closing Connection");
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			try {
				if (socketInStream != null) {
					socketInStream.close();
				}
				if (socketOutStream != null) {
					socketOutStream.close();
				}
				if (fis != null) {
					fis.close();
				}
			} catch (IOException e2) {
				e2.printStackTrace();
			}
		}
	}



	public String handleGet() {
		String result = "";
		BufferedWriter out = null;

		try {
			File file = new File("." + this.requestURI);
			if (fileServer.getDebugMode()) {
				System.out.println("Trying to open file: " + this.requestURI);
			}
			if (!file.exists()) {
				if (fileServer.getDebugMode()) {
					System.out.println("Could not find file or directory: "
							+ file.getPath());
				}
				throw new FileNotFoundException(
						"Could not find file or directory: " + file.getPath());
			}

			if (file.isDirectory()) {
				if (fileServer.getDebugMode()) {
					System.out.println("The requested resource: "
							+ this.requestURI
							+ "is a directory and listing all files");
				}
				if (file.listFiles().length == 0) {
					result = "    ";
				}
				for (File files : file.listFiles()) {
					result += files.getName() + "\r\n";
				}
			} else {
				if (file.isFile() && file.exists()) {
					if (fileServer.getDebugMode()) {
						System.out.println("The requested resource: "
								+ this.requestURI
								+ "is a file and trying to open file");
					}
					if (queryParameters != null) {
						out = new BufferedWriter(new FileWriter(file, true));
						out.write(queryParameters);
						out.flush();
						file = new File("." + this.requestURI);
						out.close();
					}
					// start reading content of the requested file
					fis = new FileInputStream(file);
					int bytes = 0;
					while ((bytes = fis.read()) != -1) {
						result += (char) bytes;
					}
					if (fileServer.getDebugMode()) {
						System.out.println("File opened and the contents are:");
						System.out.println(result);
					}
				}
			}
			if (result.length() != 0) {
				responseStatus = 200;
			} else {
				responseStatus = 400;
			}
		} catch (FileNotFoundException e) {
			responseStatus = 404;
			e.printStackTrace();
		} catch (IOException e) {
			responseStatus = 500;
			e.printStackTrace();
		}
		return result.trim();
	}

	public String handlePost() {
		String result = "";
		BufferedWriter out = null;
		try {
			File outputFile = new File("." + this.requestURI);
			outputFile.createNewFile();
			if (fileServer.getDebugMode()) {
				System.out.println("Trying to open file: " + this.requestURI);
			}
			if(outputFile.isFile()){
			out = new BufferedWriter(new FileWriter(outputFile, false));

			
			if (queryParameters != null) {
				out.write(queryParameters);
				out.flush();
				out.close();
				outputFile = new File("." + this.requestURI);
			}
			if (this.requestBody != null) {
				out.write(requestBody);
				out.flush();
				out.close();
				outputFile = new File("." + this.requestURI);
			}

			fis = new FileInputStream(outputFile);
			int c;
			while ((c = fis.read()) != -1) {
				result += (char) c;
			}
			}
			if (result.length() != 0) {
				responseStatus = 200;
			} else {
				responseStatus = 400;
			}
		} catch (IOException e) {
			// TODO: handle exception
			responseStatus = 500;
			e.printStackTrace();
		}
		return result.trim();
	}

	
	private String handleHtmlResponse(){
		String result="";
		try {
			if(responseStatus!=200){
				File html=new File("./root/ErrorTemplate.html");
				String res="";
				fis=new FileInputStream(html);
				int bytes = 0;
				while ((bytes = fis.read()) != -1) {
					res += (char) bytes;
				}
				fis.close();
				switch(responseStatus){
				case 400:
					res =res.replace("{Error Message}",  "400 Bad Request");
					break;
				case 403:
					res =res.replace("{Error Message}",  "403 Forbidden");
					break;
				case 404:
					res =res.replace("{Error Message}",  "404 Not Found");
					break;
				case 501:
					res =res.replace("{Error Message}",  "501 Not Implemented");
					break;
				case 500:
					res =res.replace("{Error Message}",  "500 Internal Server Error");
					break;
				}
				result= res;
			}else{
				File html=new File("./root/HtmlOutputTemplate.html");
				String res="";
				fis=new FileInputStream(html);
				int bytes = 0;
				while ((bytes = fis.read()) != -1) {
					res += (char) bytes;
				}
				fis.close();
				res=res.replace("{directory}", this.clientURI);
				res=res.replace("{directories}", this.clientURI);
				res=res.replace("{body}", responseBody);
				result= res;}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	public String contentType(String path) {
		String result = "";
		if (path.contains(".")) {
			path.split("\\.");
			result = Content_Type_Mapping.valueOf(path.split("\\.")[1])
					.getContent_Type();
		}
		return (result == "") ? Content_Type_Mapping.default_content_type
				.getContent_Type() : result;
	}
	
	private enum Content_Type_Mapping {
		html("text/html"), txt("text/plain"), png("image/png"), jpeg(
				"image/jpeg"), default_content_type("text/plain");
		private final String content_type;

		private Content_Type_Mapping(String content_type) {
			this.content_type = content_type;
		}

		public String getContent_Type() {
			return this.content_type;
		}
	}

	private Socket client_socket = null;
	private int clientId = 0;
	private FileServer fileServer = null;
	private BufferedReader socketInStream = null;
	private DataOutputStream socketOutStream = null;
	private FileInputStream fis = null;

	private HashMap<String, String> requestHeaders = null;
	private String requestMethod = null;
	private String requestBody = null;
	private String requestURI = null;
	private String queryParameters = null;
	private String clientURI=null;
	private int responseStatus = 0;
	private HashMap<String, String> response_headers;
	private String responseBody;
}
