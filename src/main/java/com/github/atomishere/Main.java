package com.github.atomishere;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.xml.bind.annotation.adapters.HexBinaryAdapter;
import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Main {
    public static void main(String[] args) {
        if(args.length == 0) {
            System.out.println("Usage: <version> [-minecraft (minecraft folder)]");
            return;
        }

        System.out.println("Getting official Mojang mappings.");

        File outDir = new File(System.getProperty("user.dir"), "mappings");
        if(!outDir.exists()) {
            if(!outDir.mkdirs()) {
                System.out.println("Could not create output directory!");
                return;
            }
        }

        String version = args[0];

        File minecraftFile;
        if(args.length == 3 && args[1].equalsIgnoreCase("-minecraft")) {
            minecraftFile = new File(args[2]);

            if(minecraftFile.exists()) {
                System.out.println("Can not find minecraft folder.");
                return;
            }
        } else {
            minecraftFile = getMinecraftFile();

            if(minecraftFile == null) {
                System.out.println("Can not find minecraft folder. Please specify the minecraft folder yourself using the -minecraft argument.");
                return;
            }
        }

        File versionsFile = getVersionFile(minecraftFile);
        if(versionsFile == null) {
            System.out.println("Could not find versions file!");
            return;
        }

        File versionFile = new File(versionsFile, version);
        if(!versionFile.exists()) {
           System.out.println("Could not find file for version " + version);
           return;
        }

        File jsonFile = new File(versionFile, version + ".json");
        if(!versionFile.exists()) {
            System.out.println("Could not find json file for version " + version);
            return;
        }

        JSONObject jsonObject = parseFile(jsonFile);
        if(jsonObject == null) {
            return;
        }

        JSONObject clientObject = (JSONObject) ((JSONObject) jsonObject.get("downloads")).get("client_mappings");
        JSONObject serverObject = (JSONObject) ((JSONObject) jsonObject.get("downloads")).get("server_mappings");

        String clientUrl = (String) clientObject.get("url");
        String serverUrl = (String) serverObject.get("url");

        File working = new File(outDir, version);
        if(!working.exists()) {
            if(!working.mkdirs()) {
                System.out.println("Could not create output directory.");
                return;
            }
        }

        try {
            System.out.println("Downloading client mappings...");
            download(working, "client.txt", clientUrl);
            System.out.println("Downloaded client mappings as client.txt");

            System.out.println("Downloading server mappings...");
            download(working, "server.txt", serverUrl);
            System.out.println("Downloaded server mappings as server.txt");
        } catch(IOException ex) {
            ex.printStackTrace();
            return;
        }

        System.out.println("Validating Mojang mappings.");

        File clientMappingFile = new File(working, "client.txt");
        String clientSha1;
        try {
            clientSha1 = calcSHA1(clientMappingFile);
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println("ERROR: Can not calculate client sha-1 hash. The file may still be valid but the program just can't calculate the hash.");
            e.printStackTrace();
            return;
        }

        File serverMappingFile = new File(working, "server.txt");
        String serverSha1;
        try {
            serverSha1 = calcSHA1(serverMappingFile);
        } catch (IOException | NoSuchAlgorithmException e) {
            System.out.println("ERROR: Can not calculate server sha-1 hash. The file may still be valid but the program just can't calculate the hash.");
            e.printStackTrace();
            return;
        }

        System.out.println("Validating client mappings");
        String compareClient = (String) clientObject.get("sha1");
        if(compareClient.equals(clientSha1)) {
            System.out.println("ERROR: CLIENT MAPPINGS IS NOT VALID!");
        }
        System.out.println("Client mappings validated");

        System.out.println("Validating server mappings");
        String compareServer = (String) serverObject.get("sha1");
        if(compareServer.equals(serverSha1)) {
            System.out.println("ERROR: SERVER MAPPINGS IS NOT VALID!");
        }
        System.out.println("Server mappings validated");

        System.out.println("Saved client mappings to: " + clientMappingFile.getPath());
        System.out.println("Saved server mappings to: " + serverMappingFile.getPath());
    }

    private static File getDataFolder() {
        String workingDirectory;
        String os = (System.getProperty("os.name")).toUpperCase();

        if (os.contains("WIN")) {
            workingDirectory = System.getenv("AppData");
        } else {
            workingDirectory = System.getProperty("user.home") + "/Library/Application Support";
        }

        File dataFolder = new File(workingDirectory);

        if(!dataFolder.exists()) {
            return null;
        }
        return dataFolder;
    }

    private static File getMinecraftFile() {
        File dataFolder = getDataFolder();
        if(dataFolder == null) {
            System.out.println("Can not find data folder. Please specify the minecraft folder yourself using the -minecraft argument.");
            return null;
        }

        File minecraft = new File(dataFolder, ".minecraft");
        if(!minecraft.exists()) {
            minecraft = new File(dataFolder, "minecraft");
            if(!minecraft.exists()) {
                return null;
            }
        }

        return minecraft;
    }

    private static File getVersionFile(File minecraftFolder) {
        File versionFile = new File(minecraftFolder, "versions");
        if(!versionFile.exists()) {
            return null;
        }

        return versionFile;
    }

    private static JSONObject parseFile(File jsonFile) {
        JSONParser jsonParser = new JSONParser();

        JSONObject object = null;
        try(FileReader reader = new FileReader(jsonFile)) {
            object = (JSONObject) jsonParser.parse(reader);
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return object;
    }

    private static void download(File directory, String name, String sUrl) throws IOException {
        URL url = new URL(sUrl);
        ReadableByteChannel rbc = Channels.newChannel(url.openStream());

        File file = new File(directory, name);
        if(!file.exists()) {
            if(!file.createNewFile()) {
                System.out.println("Unable to create file");
                return;
            }
        }

        FileOutputStream fos = new FileOutputStream(new File(directory, name));
        fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        fos.close();
        rbc.close();
    }

    private static String calcSHA1(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        try (InputStream input = new FileInputStream(file)) {

            byte[] buffer = new byte[8192];
            int len = input.read(buffer);

            while (len != -1) {
                sha1.update(buffer, 0, len);
                len = input.read(buffer);
            }

            return new HexBinaryAdapter().marshal(sha1.digest());
        }
    }
}
