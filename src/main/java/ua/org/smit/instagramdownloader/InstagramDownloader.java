package ua.org.smit.instagramdownloader;

import com.github.instagram4j.instagram4j.IGClient;
import com.github.instagram4j.instagram4j.exceptions.IGLoginException;
import com.github.instagram4j.instagram4j.models.media.ImageVersions;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import ua.org.smit.commontlx.filesystem.FolderCms;

public class InstagramDownloader {

    private final int urlSleep = 5; //bot ani ban
    private final int accountSleep = 15;

    private final IGClient instagramClient;

    public InstagramDownloader(
            String login,
            String password) throws IGLoginException {

        instagramClient = IGClient.builder()
                .username(login)
                .password(password)
                .login();
    }

    public void downloadAccount(String account, FolderCms accountFolder) throws IOException, InterruptedException {
        Random random = new Random();
        int secA = random.nextInt(accountSleep);
        System.out.println("Account sleep: " + secA + " sec ...");
        sleep(secA * 1000);

        AtomicLong userPk = new AtomicLong();
        instagramClient.actions().users().findByUsername(account).thenAccept(response -> {
            userPk.set(response.getUser().getPk());
        }).join();
        FeedUserRequest request = new FeedUserRequest(userPk.get());
        FeedUserResponse response = instagramClient.sendRequest(request).join();
        List<TimelineMedia> items = response.getItems();
        List<String> urls = getUrls(items);

        for (int i = 0; i < urls.size(); i++) {
            int secB = random.nextInt(urlSleep);
            System.out.println("URL sleep: " + secB + " sec ...");
            sleep(secB * 1000);

            String url = urls.get(i);
            System.out.println("Url: " + url);
            String dest = accountFolder + File.separator + i + "." + getExtension(url);
            downloadFile(instagramClient.getHttpClient(), url, dest);
        }
    }

    String getExtension(String url) {
        String urlWithoutFields = url.substring(0, url.indexOf('?'));
        String[] parts = urlWithoutFields.split("\\.");
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

    String extractUrlFromImageVersions(ImageVersions imageVersions) {
        return imageVersions.getCandidates().get(0).getUrl();
    }

    List<String> getUrls(List<TimelineMedia> timelineMedias) {
        List<String> urls = new ArrayList<>();
        for (TimelineMedia timelineMedia : timelineMedias) {
            if (timelineMedia instanceof TimelineImageMedia timelineImageMedia) {
                urls.add(extractUrlFromImageVersions(timelineImageMedia.getImage_versions2()));
            } else if (timelineMedia instanceof TimelineVideoMedia timelineVideoMedia) {
                urls.add(extractUrlFromImageVersions(timelineVideoMedia.getImage_versions2()));
            } else if (timelineMedia instanceof TimelineCarouselMedia carousel) {
                List<CarouselItem> carouselItems = carousel.getCarousel_media();
                for (CarouselItem carouselItem : carouselItems) {
                    if (carouselItem instanceof ImageCarouselItem imageCarouselItem) {
                        urls.add(extractUrlFromImageVersions(imageCarouselItem.getImage_versions2()));
                    } else if (carouselItem instanceof VideoCarouselItem videoCarouselItem) {
                        urls.add(extractUrlFromImageVersions(videoCarouselItem.getImage_versions2()));
                    }
                }
            }
        }
        return urls;
    }

}
