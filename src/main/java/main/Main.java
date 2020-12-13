package main;

import server.Server;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
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
