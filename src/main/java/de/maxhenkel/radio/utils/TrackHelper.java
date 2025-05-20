package de.maxhenkel.radio.utils;

import de.maxhenkel.radio.Radio;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

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
        initialize_queue();
    }

    public void load_folder(){
        mp3_folder = new File((Radio.SERVER_CONFIG.localMp3Folder.get()));
        if (!mp3_folder.exists()) {mp3_folder.mkdirs();}
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
            output.add(new Track(file).track_getSongName());
        }
        return output;
    }

    // call this when click on radio
    public void initialize_queue(){
        if (this.track_queue.isEmpty()){
            this.track_queue.add(get_random_track());
            this.track_queue.add(get_random_track());
        }
        //return this.track_queue.getFirst();
    }

    // call this when previous song end
    public void update_queue(){
        if(!this.track_queue.isEmpty()){
            track_queue.removeFirst();
            track_queue.add(get_random_track());
        }
    }

    public Track get_track_from_queue(){
        return this.track_queue.getFirst();
    }

    public static class Track{
        File mp3_file;
        String song_name;
        String song_artist;
        int song_duration;

        private Track(File mp3_file){
            this.mp3_file = mp3_file;
            // setting up metadata
            try {
                AudioFile track_file = AudioFileIO.read(this.mp3_file);
                Tag track_tags = track_file.getTag();
                AudioHeader audioHeader = track_file.getAudioHeader();

                this.song_name = track_tags.getFirst(FieldKey.TITLE);
                this.song_artist = track_tags.getFirst(FieldKey.ARTIST);

                this.song_duration = Integer.parseInt(String.valueOf(audioHeader.getTrackLength()));

            }
            catch (Exception e){
                // default case
                song_name =  mp3_file.getName();
                song_artist = "Unknown";
                song_duration = 60;
                Radio.LOGGER.error(e);
            }

        }

        public File track_getFileObj() { return  this.mp3_file; }
        public String track_getSongName() { return this.song_name; }
        public String track_getSongArtist() { return  this.song_artist; }
        public int track_getSongDuration() { return  this.song_duration; }
        public String track_getTimeStamp(){
            return String.format("%02d:%02d", this.song_duration / 60, this.song_duration % 60);
        }
    }
}
