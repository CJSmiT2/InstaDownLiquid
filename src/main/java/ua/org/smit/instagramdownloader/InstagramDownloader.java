package ua.org.smit.instagramdownloader;

import com.github.instagram4j.instagram4j.IGClient;
import com.github.instagram4j.instagram4j.exceptions.IGLoginException;
import com.github.instagram4j.instagram4j.models.media.timeline.CarouselItem;
import com.github.instagram4j.instagram4j.models.media.timeline.ImageCarouselItem;
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineCarouselMedia;
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineImageMedia;
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineMedia;
import com.github.instagram4j.instagram4j.models.media.timeline.TimelineVideoMedia;
import com.github.instagram4j.instagram4j.models.media.timeline.VideoCarouselItem;
import com.github.instagram4j.instagram4j.requests.feed.FeedUserRequest;
import com.github.instagram4j.instagram4j.responses.feed.FeedUserResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.lang.Thread.sleep;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ua.org.smit.commontlx.filesystem.FileCms;
import ua.org.smit.commontlx.filesystem.FolderCms;
import ua.org.smit.commontlx.filesystem.TxtFile;

public class InstagramDownloader {

    //bot ani ban
    private final int urlSleep = 5;
    private final int feedSleep = 2;
    private final int accountSleep = 15;

    private final IGClient instagramClient;

    private final boolean updateOnly;

    public InstagramDownloader(
            String login,
            String password,
            boolean updateOnly) throws IGLoginException {

        this.updateOnly = updateOnly;

        instagramClient = IGClient.builder()
                .username(login)
                .password(password)
                .login();
    }

    public void downloadAccount(String account, FolderCms accountFolder) throws IOException, InterruptedException {
        Random random = new Random();
        int secA = random.nextInt(accountSleep);
        System.out.println("Account sleep: " + secA + " sec ...");
        sleep((5 + secA) * 1000);

        System.out.println("Fetch urls...");
        AtomicLong userPk = new AtomicLong();
        instagramClient.actions().users().findByUsername(account).thenAccept(response -> {
            userPk.set(response.getUser().getPk());
        }).join();
        FeedUserRequest request = new FeedUserRequest(userPk.get());

        listFeed(random, accountFolder, instagramClient, request);
    }

    void listFeed(Random random,
            FolderCms accountFolder,
            IGClient instagramClient,
            FeedUserRequest request) throws InterruptedException, IOException {

        String nextMaxId = null;
        int feedCount = 0;
        boolean next = true;

        while (next) {

            try {
                System.out.println("Response: " + (feedCount + 1));

                if (feedCount > 0) {
                    request.setMax_id(nextMaxId);
                    System.out.println("Account: '"
                            + accountFolder.getName() + "',"
                            + " nextMaxId: " + nextMaxId);
                }

                FeedUserResponse response = instagramClient.sendRequest(request).join();

                List<TimelineMedia> items = response.getItems();
                List<String> urls = getUrls(items);

                removeExists(urls, accountFolder);

                if (this.updateOnly && urls.isEmpty()) {
                    next = false;
                    System.out.println("Skip download account: " + accountFolder.getName());
                    break;
                }

                downloadFromUrls(urls, random, accountFolder, instagramClient);

                nextMaxId = response.getNext_max_id();

                feedCount++;
                next = (nextMaxId != null);

            } catch (SocketTimeoutException ex) {
                System.err.println("ERROR: " + ex);
                System.out.println("Retry...");
            }
            sleep(feedSleep * 1000);
        }
    }

    void downloadFromUrls(List<String> urls,
            Random random,
            FolderCms accountFolder,
            IGClient instagramClient) throws InterruptedException, IOException {
        
        TxtFile txt = new TxtFile(accountFolder + File.separator + "downloaded.txt");

        for (int i = 0; i < urls.size(); i++) {
            int secB = random.nextInt(urlSleep);
            System.out.println("URL sleep: " + secB + " sec ...");
            sleep((++secB) * 1000);

            String fileName = getFileName(urls.get(i));
            System.out.println("[" + (i + 1) + "/" + urls.size() + "], "
                    + "'" + accountFolder.getName() + "' "
                            + "Url: " + fileName);
            String dest = accountFolder + File.separator + fileName;
            downloadFile(instagramClient.getHttpClient(), urls.get(i), dest);
            txt.addToFile(fileName);
        }
    }

    String getFileName(String url) {
        String urlWithoutFields = url.substring(0, url.indexOf('?'));
        String[] parts = urlWithoutFields.split("/");
        return parts[parts.length - 1];
    }

    void downloadFile(OkHttpClient client, String url, String destinationPath) throws IOException {
        Request request = new Request.Builder().url(url).build();
        Response response = client.newCall(request).execute();

        InputStream is = response.body().byteStream();
        BufferedInputStream input = new BufferedInputStream(is);

        File destinationFile = new File(destinationPath);
        OutputStream output = new FileOutputStream(destinationFile);

        byte[] data = new byte[1024];

        int count = 0;
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
        }

        output.flush();
        output.close();
        input.close();
        
    }

    List<String> getUrls(List<TimelineMedia> timelineMedias) {
        List<String> urls = new ArrayList<>();
        for (TimelineMedia timelineMedia : timelineMedias) {
            if (timelineMedia instanceof TimelineImageMedia timelineImageMedia) {
                urls.add(timelineImageMedia.getImage_versions2().getCandidates().get(0).getUrl());

            } else if (timelineMedia instanceof TimelineVideoMedia timelineVideoMedia) {
                urls.add(timelineVideoMedia.getVideo_versions().get(0).getUrl());

            } else if (timelineMedia instanceof TimelineCarouselMedia carousel) {
                List<CarouselItem> carouselItems = carousel.getCarousel_media();
                for (CarouselItem carouselItem : carouselItems) {
                    if (carouselItem instanceof ImageCarouselItem imageCarouselItem) {
                        urls.add(imageCarouselItem.getImage_versions2().getCandidates().get(0).getUrl());

                    } else if (carouselItem instanceof VideoCarouselItem videoCarouselItem) {
                        urls.add(videoCarouselItem.getVideo_versions().get(0).getUrl());

                    }
                }
            }
        }
        return urls;
    }

    private void removeExists(List<String> urls, FolderCms accountFolder) {
        List<String> downloadedFiles = getDownloaded(accountFolder);

        Iterator<String> i = urls.iterator();
        while (i.hasNext()) {
            String url = i.next();
            String fileName = getFileName(url);

            if (accountFolder.isFileExist(fileName)
                    || isExistInList(fileName, downloadedFiles)) {
                i.remove();
//                System.out.println("IGNORE: " + fileName);
            }

        }

    }

    private boolean isExistInList(String name, List<String> list) {
        for (String item : list) {
            if (item.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getDownloaded(FolderCms accountFolder) {
        TxtFile txt = new TxtFile(accountFolder + File.separator + "downloaded.txt");
        if (!txt.exists()) {
            System.out.println("'" + txt.getName() + "' Not exist! Create new: '" + txt + "'");
            List<FileCms> files = accountFolder.getFilesByExtensions(
                    Arrays.asList(
                            FileCms.Extension.JPG,
                            FileCms.Extension.MP4,
                            FileCms.Extension.WEBM));

            for (FileCms media : files) {
                txt.addToFile(media.getName());
            }

        }
        if (!txt.exists()){
            return new ArrayList<>();
        }
        return txt.readByLines();
    }
}
