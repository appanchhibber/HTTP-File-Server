package com;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

public class Httpfs {

	private FileServer file_server;
	public Httpfs(){
		this.file_server=new FileServer();
	}
	

	public void Start(OptionSet opts){
		file_server.setPort((int)opts.valueOf("p"));
		file_server.setDirectory((String)opts.valueOf("d"));
		if(opts.specs().toString().contains("v")){
		file_server.setDebugMode(true);	
		}
		file_server.makeServer();
	}
	public static void main(String[] args) {
		OptionParser parser = new OptionParser();
		parser.accepts("v","Prints debugging messages.");
		   parser.accepts("p","Specifies the port number that the server will listen and serve at.").withRequiredArg().ofType(Integer.class).describedAs("port").defaultsTo(8080);
		   parser.accepts("d","Specifies the directory that the server will use to read/write requested files. Default is the current directory when launching the application.").withRequiredArg().ofType(String.class).describedAs("directory");
		   parser.accepts("help","For help").forHelp();
		OptionSet opts = parser.parse(args);
		try {
			if(opts.specs().toString().contains("help")){
				parser.printHelpOn( System.out );
				System.exit(0);
			}

		Httpfs server=new Httpfs();
		server.Start(opts);
		}catch(Exception e){
			e.printStackTrace();
		}
	}

}
