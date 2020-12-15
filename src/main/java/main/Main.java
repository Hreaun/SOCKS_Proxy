package main;

import org.apache.log4j.BasicConfigurator;
import org.xbill.DNS.ResolverConfig;
import server.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        BasicConfigurator.configure();
        int port = 0;
        try {
            if (args.length != 1) {
                throw new IllegalArgumentException("Enter port to bind server");
            }
            port = Integer.parseInt(args[0]);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return;
        }

        try {
            Server server = new Server(port);
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
