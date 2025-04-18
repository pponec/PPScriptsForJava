// Mp3Player
// Java 17+ is required
// Home Page: https://github.com/pponec/DirectoryBookmarks/blob/development/utils/Mp3Player.java
// License: Apache License, Version 2.0
// Usage: java Mp3PlayerGenerator [run] [port]

package net.ponec.script;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;

public class Mp3Player {

    private final String homePage = "https://github.com/pponec/Mp3PlayerGenerator";
    private final String appName = getClass().getName();
    private final String appVersion = "1.3.5";
    private final String outputFile = "index.html";
    private final Charset charset = StandardCharsets.UTF_8;
    private HttpServer server = null;

    public static void main(String[] args) throws Exception {
        var player = new Mp3Player();
        var cmd = args.length >= 1 ? args[0] : "";
        switch (cmd) {
            case "help" -> {
                player.printHelpAndExit();
            }
            case "run", "server" -> {
                try {
                    var port = args.length >= 2 ? Integer.parseInt(args[1]) : 8000;
                    player.run(port);
                } catch (Exception e) {
                    player.printHelpAndExit();
                }
            }
            default -> { // "make"
                var mp3List = player.getSoundFilesSorted();
                var html = player.buildHtmlPlayer(mp3List);
                Files.writeString(Paths.get(player.outputFile), html, player.charset);
                System.out.printf("The player is written to the file: %s%n", player.outputFile);
            }
        }
    }

    void printHelpAndExit() {
        System.out.printf("Script '%s' v%s (%s)%n", appName, appVersion, homePage);
        System.out.printf("Usage version: %s%n", appName);
        System.exit(1);
    }

    CharSequence buildHtmlPlayer(List<Path> mp3List) {
        var songFiles = mp3List.stream()
                .map(s -> "\"" + escapeTextForJS(s.toString()) + "\"")
                .collect(Collectors.joining("\n\t, "));
        var params = Map.of(
                "songFiles", songFiles,
                "title", getCurrentDirectoryName(),
                "charset", charset,
                "appName", appName,
                "appVersion", appVersion,
                "homePage", homePage);
        return format(htmlTemplate(), params);
    }

    public String escapeTextForJS(String fileName) {
        if (fileName == null) {
            return "";
        }
        var escaped = new StringBuilder();
        for (var c : fileName.toCharArray()) {
            switch (c) {
                case '\\': escaped.append("\\\\"); break;
                case '"':  escaped.append("\\\""); break;
                case '\'': escaped.append("\\'"); break;
                case '\n': escaped.append("\\n"); break;
                case '\r': escaped.append("\\r"); break;
                case '\t': escaped.append("\\t"); break;
                case '/':  escaped.append("\\/"); break; // Optional for security
                case '<':  escaped.append("\\x3C"); break; // Prevents HTML injection
                case '>':  escaped.append("\\x3E"); break; // Prevents HTML injection
                default:
                    if (c < 32 || c > 126) {
                        escaped.append(String.format("\\u%04x", (int) c));
                    } else {
                        escaped.append(c);
                    }
            }
        }
        return escaped.toString();
    }

    /** Return all 'mp3' and 'ogg' files. */
    List<Path> getSoundFilesSorted() throws IOException {
        var filePattern = Pattern.compile("\\.(?i)(mp3|ogg)$");
        var files = new FinderUtilitiy(filePattern, new PathComparator())
                .findFiles(Path.of(""))
                .getFileCollector();
        files.sort(new PathComparator());
        return files;
    }

    String htmlTemplate() {
        return """
<!DOCTYPE html>
<html>
<head>
	<title>${title}</title>
	<meta charset="${charset}"/>
	<meta name="generator" content="${appName} v${appVersion}, ${homePage}"/>
	<style>
        h1 {
            color: steelblue;
        }
        #playlist {
            column-count: 2;
            column-gap: 2rem;
        }
        #playlist li {
            cursor: pointer;
        }
        #playlist li.current {
            font-weight: bold;
        }
        #repeater {
            margin-left: 17px;
        }
        #audioPlayer {
            width: 100%;
            border: none;
        }
        .footer, .footer a {
            margin-top: 30px;
            margin-left: 7px;
            font-style: italic;
            font-size: 0.9rem;
            color: Gray;
        }
    </style>
</head>
<body>
	<h1>${title}</h1>
	<ol id="playlist"></ol>
	<label id="repeater">File repeater:
		<input type="checkbox"/>
	</label>
	<audio id="audioPlayer" controls></audio>
	<p id="currentSong"></p>
	<script>
        var playlist = [ ${songFiles} ];
        var audioPlayer = document.getElementById('audioPlayer');
        var playlistElement = document.getElementById('playlist');
        var currentSongIndex = 0;
        var currentSongElement = document.getElementById('currentSong');
        var repeater = document.querySelector('#repeater input');
        var dayCount = 30;

        // Function to set cookie value
        function setCookie(name, value, days) {
            var date = new Date();
            date.setTime(date.getTime() + (days * 24 * 60 * 60 * 1000));
            var cookie = name + "=" + encodeURIComponent(value) + "; path=/; expires=" + date.toUTCString();        
            document.cookie = cookie;
        }
  
        // Get cookie value
        function getCookie(name) {
            let regexp = '(^| )' + name + '=([^;]+)';
            let match = document.cookie.match(new RegExp(regexp));
            return match ? decodeURIComponent(match[2]) : "";
        }
                
        // Restore last played song index from cookie to the Integer type
        function loadCurrentSongIndex() {
            var savedIndex = getCookie("currentSongIndex");
            return savedIndex !== null ? parseInt(savedIndex, 10) % playlist.length : 0;
        }

        // Generate playlist:
        playlist.forEach(function(song, index) {
            var listItem = document.createElement('li');
            listItem.innerText = ' ' + playlist[index];
            listItem.onclick = function() {
                playSong(index);
                // Disable a scrolling to the audio player:
                var currentPosition = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0;
                audioPlayer.focus();
                window.scrollTo(0, currentPosition);
            };
            playlistElement.appendChild(listItem);
        });

        function playSong(index) {
            currentSongIndex = index;
            var songUrl = playlist[index];
            audioPlayer.src = encodeURI(songUrl);
            audioPlayer.play();
            updateCurrentSongText();
            updatePlaylistHighlight();
        }

        audioPlayer.addEventListener('ended', function() {
            if (!repeater.checked) {
                currentSongIndex = (currentSongIndex + 1) % playlist.length;
            }
            playSong(currentSongIndex);
        });

        function updateCurrentSongText() {
            var title = 'Now playing: ' + (currentSongIndex + 1) + ". " + playlist[currentSongIndex];
            currentSongElement.innerText = title;
            setCookie("currentSongIndex", currentSongIndex, dayCount);
        }
        
       function setColumCount() {
            var approxCharWidth = 10; // 10px per character
            var charsPerColumn = 70; 
            var columnCount = Math.floor(window.innerWidth / approxCharWidth / charsPerColumn ) + 1;
            document.getElementById("playlist").style.columnCount = columnCount;
        }

        function updatePlaylistHighlight() {
            var playlistItems = playlistElement.getElementsByTagName('li');
            for (var i = 0; i < playlistItems.length; i++) {
                if (i === currentSongIndex) {
                    playlistItems[i].classList.add('current');
                } else {
                    playlistItems[i].classList.remove('current');
                }
            }
        }

        window.addEventListener("resize", setColumCount);
        window.addEventListener("DOMContentLoaded", setColumCount);
        window.addEventListener("load", () => playSong(loadCurrentSongIndex()));
    </script>
    <div class="footer">
        Generated by the <a href="https://github.com/pponec/Mp3PlayerGenerator">${appName}</a> version ${appVersion}.
    </div>
</body>
</html>
               """;
    }

    /** Join a template with arguments (a method from an Ujorm framework) */
    final CharSequence format(String msg, Map<String, ?> args) {
        if (msg == null || args == null) {
            return String.valueOf(msg);
        }
        final var begTag = "${";
        final var endTag = '}';
        final var result = new StringBuilder(32 + msg.length());
        int i, last = 0;
        while ((i = msg.indexOf(begTag, last)) >= 0) {
            final var end = msg.indexOf(endTag, i);
            final var key = msg.substring(i + begTag.length(), end);
            final var val = args.get(key);
            if (val != null) {
                result.append(msg, last, i).append(val);
            } else {
                result.append(msg, last, end + 1);
            }
            last = end + 1;
        }
        return result.append(msg, last, msg.length());
    }

    String getCurrentDirectoryName() {
        try {
            return Paths.get("").toAbsolutePath().getFileName().toString();
        } catch (RuntimeException e) {
            return "MP3 player";
        }
    }

    static final class FinderUtilitiy {
        private final Pattern filePattern;
        private final Comparator<Path> pathComparator;
        private final List<Path> fileCollector = new ArrayList<>();

        public FinderUtilitiy(Pattern filePattern, Comparator<Path> comparator) {
            this.filePattern = filePattern;
            this.pathComparator = comparator;
        }

        public List<Path> getFileCollector() {
            return fileCollector;
        }

        public FinderUtilitiy findFiles(Path dir) throws IOException {
            try (var fileStream = Files.list(dir)) {
                fileStream.filter(Files::isReadable)
                        .sorted(pathComparator)
                        .forEach(file -> {
                            if (Files.isDirectory(file)) {
                                try {
                                    findFiles(file);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            } else if (filePattern.matcher(file.toString()).find()) {
                                fileCollector.add(file);
                            }
                        });
            }
            return this;
        }
    }


    /** Compare files by a name, the directory last */
    static class PathComparator implements Comparator<Path> {
        @Override
        public int compare(final Path p1, final Path p2) {
            final var d1 = Files.isDirectory(p1);
            final var d2 = Files.isDirectory(p2);
            if (d1 != d2) {
                return d1 ? 1 : -1;
            } else {
                return removeDiacritics(p1).compareTo(
                       removeDiacritics(p2));
            }
        }

        /** Remove diacritics and some common separator characters. */
        String removeDiacritics(final Path file) {
            var input = file.toString();
            var result = Normalizer.normalize(input, Normalizer.Form.NFD);
            result = result.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
            result = result.replaceAll("[\\-+\\.|:;,_/ ]+", " ");
            result = result.toLowerCase(Locale.ENGLISH);
            return result;
        }
    }

    public void run(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new FileHandler());
        server.createContext("/stop", new StopHandler());
        server.setExecutor(Executors.newFixedThreadPool(3)); // Uses a thread pool
        server.start();
        System.out.printf("Play music on the site: http://localhost:%s/%n", port);

        // Prevent program from exiting immediately
        synchronized (Mp3Player.class) {
            try {
                Mp3Player.class.wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    class FileHandler implements HttpHandler {
        private final Path rootDir = Path.of(".").toAbsolutePath().normalize();

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            var requestedPath = exchange.getRequestURI().getPath();
            var param = requestedPath.substring(1);
            if (param.isEmpty()) {
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=" + charset);
                var mp3List = getSoundFilesSorted();
                var html = buildHtmlPlayer(mp3List).toString().getBytes(charset);
                exchange.sendResponseHeaders(200, html.length);
                exchange.getResponseBody().write(html);
                return;
            }
            var filePath = rootDir.resolve(param).normalize();

            // Security check: prevent access outside the root directory
            if (!filePath.startsWith(rootDir) || !Files.exists(filePath) || Files.isDirectory(filePath)) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            // Set correct MIME type
            var mimeType = Files.probeContentType(filePath);
            if (mimeType == null) mimeType = "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", mimeType);
            exchange.sendResponseHeaders(200, Files.size(filePath));

            // Send the file
            try (var os = exchange.getResponseBody(); InputStream is = Files.newInputStream(filePath, StandardOpenOption.READ)) {
                is.transferTo(os);
            }
        }
    }

    class StopHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Server is shutting down...";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
            System.out.println("Stopping server...");
            server.stop(0);
        }
    }
}