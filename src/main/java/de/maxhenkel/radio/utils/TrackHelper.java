package de.maxhenkel.radio.utils;

import de.maxhenkel.radio.Radio;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;

import java.io.File;
import java.io.IOException;
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
    public File mp3_folder;
    public File[] mp3_lists;
    public ArrayList<Track> track_queue;

    public TrackHelper() throws IOException {
        track_queue = new ArrayList<Track>(2);
        load_folder();
        queue_update();
    }


    public void load_folder(){
        mp3_folder = new File((Radio.SERVER_CONFIG.localMp3Folder.get()));
        if (!mp3_folder.exists()) {
            if(!mp3_folder.mkdirs()) {Radio.LOGGER.fatal("Cannot create directory!");};
        }
        mp3_lists = mp3_folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp3"));
    }

    public Track get_random_track(){
        if (this.mp3_lists != null) {
            int index = track_randomizer.nextInt(this.mp3_lists.length);
            File target_mp3 = this.mp3_lists[index];
            return (new Track(target_mp3));
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

    // call this when previous song end
    public void queue_update(){
        while (this.track_queue.size() < 2){
            this.track_queue.add(get_random_track());
        }
    }

    public Track queue_poll(){
        // return first value
        Track out_track = track_queue.getFirst();
        track_queue.removeFirst();
        queue_update();
        return out_track;
    }

    public Track queue_peek(int index){
        return track_queue.get(index);
    }

    public static class Track{
        public File mp3_file;
        public String mp3_abspath;
        public String song_name;
        public String song_author;
        public long song_length;


        private Track(File mp3_file){
            this.mp3_file = mp3_file;
            this.mp3_abspath = mp3_file.getAbsolutePath().replace("\\.\\", "\\");
            File test = new File(mp3_abspath);

            //Radio.LOGGER.info("{} {}",mp3_abspath, Boolean.toString(test.exists()));
            try {
                AudioFile audioFile = AudioFileIO.read(mp3_file);
                AudioHeader audioHeader = audioFile.getAudioHeader();
                song_name = audioFile.getTag().getFirst(FieldKey.TITLE);
                song_author = audioFile.getTag().getFirst(FieldKey.ARTIST);
                song_length = audioHeader.getTrackLength();

            } catch (Exception e){
                Radio.LOGGER.warn("Cannot load mp3 through jaudiotagger");
                song_name = "Unknown";
            }

        }


    }
}
