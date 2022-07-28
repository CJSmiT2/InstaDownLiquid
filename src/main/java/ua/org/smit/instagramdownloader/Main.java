package ua.org.smit.instagramdownloader;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;
import ua.org.smit.commontlx.filesystem.FolderCms;
import ua.org.smit.commontlx.filesystem.TxtFile;

public class Main {

    public static void main(String[] args) throws Exception {

        String programmFolder = args[0];

        System.out.println("Program folder = " + programmFolder);

        Properties appProps = new Properties();
        appProps.load(new FileInputStream(programmFolder + File.separator + "app.properties"));

        String login = appProps.getProperty("login");
        String password = appProps.getProperty("password");
        boolean updateOnly = Boolean.valueOf(appProps.getProperty("update_only"));

        System.out.println("Login: '" + login + "', password: '" + password + "'");

        InstagramDownloader instagram = new InstagramDownloader(login, password, updateOnly);

        TxtFile txt = new TxtFile(programmFolder + File.separator + "accounts.txt");
        for (String account : txt.readByLines()) {
            System.out.println("Start download account: '" + account + "'");
            FolderCms accountFolder
                    = new FolderCms(programmFolder + File.separator
                            + "accounts" + File.separator + account);

            instagram.downloadAccount(account, accountFolder);
        }

    }

}
