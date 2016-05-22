package com.netbric.s5.conductor;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.commons.cli.CommandLine;

import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netbric.s5.cluster.ClusterManager;

public class Main
{
	static final Logger logger = LoggerFactory.getLogger(Main.class);
	private static void printUsage()
	{
		System.out.println("Usage: java com.netbric.s5.conductor -c <s5_config_file> -i <node_index>");
	}
	public static void main(String[] args)
	{
		Options options = new Options();

		// add t option
		options.addOption("c", true, "s5 config file path");
		options.addOption("i", true, "conductor node index");
		options.addOption("h", "help", false, "conductor node index");
		
		CommandLineParser cp = new DefaultParser();
		CommandLine cmd;
		try
		{
			cmd = cp.parse(options, args);
		}
		catch (ParseException e1)
		{

			e1.printStackTrace();
			System.exit(1);
			return;
		}
		if(cmd.hasOption("h"))
		{
			printUsage();
			System.exit(1);
		}

		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
		Server server = new Server(2000);

		// zookeeper config file
		String cfgPath = cmd.getOptionValue("c");
		if(cfgPath == null)
		{
			cfgPath = "/etc/s5/s5.conf";
			logger.warn("-c not specified, use {}", cfgPath);
		}
		String index =  cmd.getOptionValue("i");
		if(index == null)
		{
			printUsage();
			System.exit(1);
		}
		Config cfg = new Config(cfgPath);

		InetAddress ia = null;
		try
		{
			ia = InetAddress.getLocalHost();
		}
		catch (UnknownHostException e1)
		{
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		String managmentIp = ia.getHostAddress();

		managmentIp = cfg.getString("conductor." + cmd.getOptionValue("i"), "mngt_ip", managmentIp);
		String zkIp = cfg.getString("zookeeper", "ip", null);
		if(zkIp == null)
		{
			System.err.println("zookeeper ip not specified in config file");
			System.exit(1);
		}
		try
		{
			ClusterManager.registerAsConductor(managmentIp, zkIp);
		}
		catch (Exception e1)
		{
			e1.printStackTrace();
			logger.error("Failt to connect zookeeper:", e1);
		}
		ClusterManager.waitToBeMaster(managmentIp);
		MetaVolume.mount();

		// Add a single handler on context "/hello"
		ContextHandler context = new ContextHandler();
		context.setContextPath("/s5c");
		context.setHandler(new S5RestfulHandler());

		// Can be accessed using http://localhost:8080/hello

		server.setHandler(context);

		// Start the server
		try
		{
			server.start();
		}
		catch (Exception e)
		{

			e.printStackTrace();
			logger.error("Failt to start jetty server:", e);
		}
		try
		{
			server.join();
		}
		catch (InterruptedException e)
		{
			logger.info("Conductor stop for interrupted by {}", e.getMessage());
		}
	}

}
