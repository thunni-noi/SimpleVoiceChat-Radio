package de.maxhenkel.radio.utils;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.maxhenkel.radio.Radio;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.lang.reflect.Type;

public class TrackJsonUtils {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private File jsonFile;

    TrackJsonUtils(File file){
        this.jsonFile = file;
    }

    TrackJsonUtils(String filePath){
        this.jsonFile = new File(filePath);
    }

    public void saveTrackJson(ArrayList<TrackHelper.Track> allTracks){
        try (FileWriter writer = new FileWriter(this.jsonFile)){
            GSON.toJson(allTracks, writer);
        } catch (IOException e){
            Radio.LOGGER.error(e);
        }
    }

    public ArrayList<TrackHelper.Track> readTrackJson(){
        try (FileReader reader = new FileReader(this.jsonFile)){
            Type listType = new TypeToken<ArrayList<TrackHelper.Track>>(){}.getType();
            ArrayList<TrackHelper.Track> tracks = GSON.fromJson(reader, listType);
            for (TrackHelper.Track t: tracks){
                t.loadFile();
                if(!t.track_file.exists()){
                    tracks.remove(t);
                }
                Radio.LOGGER.info(" ({}) {} - {} {} {} {}", t.track_path, t.track_title, t.track_artist, t.track_durations, t.track_bpm, t.track_enabled);
            }
            return tracks;
        } catch (IOException e){
            Radio.LOGGER.error(e);
            return null;
        }
    }





}
