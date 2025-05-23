package de.maxhenkel.radio.utils;

import de.maxhenkel.radio.Radio;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Objects;
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
    public ArrayList<Track> all_track = new ArrayList<Track>();
    public ArrayList<Track> track_queue = new ArrayList<Track>();

    public TrackHelper() {
        track_queue = new ArrayList<Track>(2);
        load_folder(mp3FolderPath);
        load_track_noJson();
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
            if (loaded_track.song_valid){
                this.all_track.add(loaded_track);
            }
        }
    }



    public Track get_random_track(){
        if (this.mp3_lists != null) {
            int index = track_randomizer.nextInt(this.mp3_lists.length);
            return (this.all_track.get(index));
        }
        Radio.LOGGER.error("MP3 Folder hasn't load yet!");
        return null;
    }

    public ArrayList<String> get_all_available_track_name(){
        ArrayList<String> output = new ArrayList<String>();
        for (File file : this.mp3_lists){
            output.add(new Track(file).song_name);
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
        public File mp3_file;
        public String mp3_abspath;
        public String song_name;
        public String song_author;
        public long song_length;
        public long song_bpm;
        public boolean song_valid = false;


        private Track(File mp3_file){
            this.mp3_file = mp3_file;
            this.mp3_abspath = mp3_file.getAbsolutePath().replace("\\.\\", "\\");

            //Radio.LOGGER.info("{} {}",mp3_abspath, Boolean.toString(test.exists()));
            try {
                AudioFile audioFile = AudioFileIO.read(mp3_file);
                AudioHeader audioHeader = audioFile.getAudioHeader();
                song_name = audioFile.getTag().getFirst(FieldKey.TITLE);
                song_author = audioFile.getTag().getFirst(FieldKey.ARTIST);
                song_length = audioHeader.getTrackLength();
                song_bpm = 2000;
                song_valid = true;

            } catch (Exception e) {
                Radio.LOGGER.warn("Cannot load mp3 through jaudiotagger");
                song_valid = false;
            }

        }

        private Track(File mp3_file, String song_title, String song_author, long song_length, long song_bpm){
            song_valid = true;
            this.mp3_file = mp3_file;
            this.song_name = song_title;
            this.song_author = song_author;
            this.song_length = song_length;
            this.song_bpm = song_bpm;
        }


    }
}
