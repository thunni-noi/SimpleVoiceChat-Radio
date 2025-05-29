package de.maxhenkel.radio.utils;

import de.maxhenkel.radio.Radio;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;


public class TrackHelper {
    // disable jaudiotagger logger
    static {
        Logger[] pin = new Logger[]{Logger.getLogger("org.jaudiotagger")};

        for (Logger l : pin)
            l.setLevel(Level.OFF);
    }

    private final Random track_randomizer = new Random();
    public String mp3FolderPath = Radio.SERVER_CONFIG.localMp3Folder.get();
    public File mp3_folder;
    public File[] mp3_lists;
    public ArrayList<Track> tracks_allLoaded = new ArrayList<Track>();
    public ArrayList<Track> tracks_enabled = new ArrayList<>();
    public ArrayList<Track> track_queue;
    public TrackJsonUtils jsonUtils = new TrackJsonUtils(mp3FolderPath + "/tracksInfo.json");

    public TrackHelper() {
        track_queue = new ArrayList<Track>(2);
        load_folder(mp3FolderPath);
        load_track_fromJson();
    }


    public void load_folder(String folderPath){
        mp3_folder = new File(folderPath);
        if (!mp3_folder.exists()) {
            if(!mp3_folder.mkdirs()) {Radio.LOGGER.fatal("Cannot create directory!");};
        }
        mp3_lists = mp3_folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));
    }


    private void load_track_noJson(){
        for (File mp3file : this.mp3_lists){
            Track loaded_track = new Track(mp3file);
            this.tracks_allLoaded.add(loaded_track);
            Radio.LOGGER.info("Loaded manually");
        }
        this.tracks_enabled.addAll(this.tracks_allLoaded);
        jsonUtils.saveTrackJson(this.tracks_allLoaded);

    }

    private void load_track_fromJson(){
        ArrayList<Track> loaded_track = jsonUtils.readTrackJson();
        if (loaded_track != null) {
            this.tracks_allLoaded = loaded_track;
            Radio.LOGGER.info("Loaded from JSON!");

            for (Track track : this.tracks_allLoaded){
                if (track.track_enabled){
                    this.tracks_enabled.add(track);
                }
            }

        }
        else {
            load_track_noJson();
        }
    }



    public Track get_random_track(){
        if (this.mp3_lists != null) {
            int index = track_randomizer.nextInt(this.mp3_lists.length);
            return (this.tracks_allLoaded.get(index));
        }
        Radio.LOGGER.error("MP3 Folder hasn't load yet!");
        return null;
    }

    public ArrayList<String> get_all_available_track_name(){
        ArrayList<String> output = new ArrayList<String>();
        for (File file : this.mp3_lists){
            output.add(new Track(file).track_title);
        }
        return output;
    }

    public static class QueueManager extends TrackHelper{
        int queueSize;
        ArrayList<Track> queue = new ArrayList<Track>();

        QueueManager(int queueSize){
            super();
            this.queueSize = queueSize;
        }

        public QueueManager(){
            this(3); // default queue size
        }

        public void update(){
            while (queue.size() < queueSize){
                queue.add(super.get_random_track());
            }
        }

        public Track poll(){
            Track nextTrack = queue.getFirst();
            queue.removeFirst();
            this.update();
            return nextTrack;
        }

        public Track peek(int index){
            return queue.get(index);
        }

        public void clear(){
            if (queue != null) { queue.clear();};
        }
    }


    public static class Track{
        public transient File track_file;
        public String track_path;
        public String track_title;
        public String track_artist;
        public long track_durations;
        public float track_bpm;
        public boolean track_enabled = false;


        private Track(File track_file){
            this.track_file = track_file;
            this.track_path = track_file.getAbsolutePath().replace("\\.\\", "\\");

            //Radio.LOGGER.info("{} {}",mp3_abspath, Boolean.toString(test.exists()));
            try {
                AudioFile audioFile = AudioFileIO.read(track_file);
                AudioHeader audioHeader = audioFile.getAudioHeader();
                track_title = audioFile.getTag().getFirst(FieldKey.TITLE);
                track_artist = audioFile.getTag().getFirst(FieldKey.ARTIST);
                track_durations = audioHeader.getTrackLength();
                track_bpm = 80;
                track_enabled = true;

            } catch (Exception e) {
                Radio.LOGGER.warn("Cannot load mp3 through jaudiotagger");
                track_enabled = false;
            }

        }

        public Track(String track_path, String track_title, String track_artist, long track_durations, long track_bpm, boolean track_enabled){
            this.track_path = track_path;
            this.track_title = track_title;
            this.track_artist = track_artist;
            this.track_durations = track_durations;
            this.track_bpm = track_bpm;
            this.track_enabled = track_enabled;
        }

        public void loadFile(){
            this.track_file = new File(this.track_path);
        }


    }
}
