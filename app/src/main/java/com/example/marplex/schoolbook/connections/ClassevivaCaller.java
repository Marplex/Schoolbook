package com.example.marplex.schoolbook.connections;

import android.content.Context;
import android.util.Log;

import com.example.marplex.schoolbook.interfaces.ClassevivaAgenda;
import com.example.marplex.schoolbook.interfaces.ClassevivaCallback;
import com.example.marplex.schoolbook.interfaces.ClassevivaLoginCallback;
import com.example.marplex.schoolbook.models.Evento;
import com.example.marplex.schoolbook.models.Voto;
import com.example.marplex.schoolbook.utilities.Credentials;
import com.example.marplex.schoolbook.utilities.Cripter;
import com.example.marplex.schoolbook.utilities.SharedPreferences;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by marco on 1/27/16.
 */
public class ClassevivaCaller {

    private final String BASE_PATH = "http://schoolbook.cloudno.de/";
    private final String BASE_PATH2 = "https://infinite-harbor-75813.herokuapp.com/";
    protected final String TAG = "Classeviva Login";

    ClassevivaLoginCallback mClassevivaLoginCallback;
    ClassevivaCallback mCallback;

    String mUser, mPassword;
    Context c;

    ClassevivaAgenda agendaCallback;

    OkHttpClient client;

    /**
     * ClassevivaCaller constructor. Use it to import user datas.
     *
     * @param username  User's username
     * @param password  User's mPassword
     * @param classevivaLoginCallback  Interface which return the requested html string
     * @param c  Activity context
     *
     */
    public ClassevivaCaller(String username, String password, ClassevivaLoginCallback classevivaLoginCallback, Context c){
        this.mUser = username;
        this.mPassword = password;
        this.c = c;
        this.mClassevivaLoginCallback = classevivaLoginCallback;
        client = new OkHttpClient();
    }

    public ClassevivaCaller(ClassevivaCallback callback, Context context) {
        this.mCallback = callback;
        this.mUser = Credentials.getName(context);
        this.mPassword = Credentials.getPassword(context);
        client = new OkHttpClient();
        this.c = context;
    }

    private void run(String url, Callback callback) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(callback);
    }

    /**
     * Call Classeviva login page with saved or requested credentials
     */
    public void doLogin() {
        //Perform new HTTP call
        try {
            run(BASE_PATH + "PNIT0003/" + mUser + "/" + mPassword, new Callback() {
                @Override public void onFailure(Call call, IOException e) { }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String json = response.body().string();
                        Log.d(TAG, "onResponse: "+json);
                        JSONObject object = new JSONObject(json);

                        boolean success = object.getString("status").equals("OK") ? true : false;
                        if(success) Credentials.saveCredentials(c, mUser, mPassword, object.getString("sessionId"));

                        mClassevivaLoginCallback.onLoginDone(success);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call ClasseViva votes page with the current session
     */
    public void getVotes(){
        //Perform new HTTP call
        try {
            String session = Credentials.getSession(c);
            run(BASE_PATH + session + "/votes", new Callback() {
                @Override public void onFailure(Call call, IOException e) { }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String json = response.body().string();
                        if( json.equals("{}") || json.startsWith("<") ){
                            //Set credentials from storage
                            ClassevivaCaller.this.mUser = Credentials.getName(c);
                            ClassevivaCaller.this.mPassword = Cripter.decriptString(Credentials.getPassword(c));

                            //Get a new session re-performing login
                            newSession(ClassevivaCaller.class.getMethod("getVotes", null));
                            return;
                        }
                        JSONObject object = new JSONObject(json);

                        ArrayList<String> subjects = new ArrayList<>();
                        ArrayList<Voto> votes = new ArrayList<>();

                        for(int i = 0; i<object.names().length(); i++){
                            subjects.add(getMateria(object.names().getString(i)));
                        }for(int i = 0; i<object.names().length(); i++){
                            JSONArray array = object.getJSONArray(object.names().getString(i));
                            for(int x = 0; x<array.length(); x++){
                                JSONObject voteObject = array.getJSONObject(x);
                                String voto = voteObject.getString("vote");
                                String subject = subjects.get(i);
                                String date = voteObject.getString("date");
                                String type = voteObject.getString("type");
                                int period = voteObject.getInt("period");
                                Voto vote = new Voto(voto, subject, date, type, period);
                                vote.setSpecial(voteObject.getBoolean("special"));
                                votes.add(vote);
                            }
                        }
                        SharedPreferences.saveString(c, "materie", "materie", new Gson().toJson(subjects));
                        mCallback.onResponse(votes);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (NoSuchMethodException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Make a request to classeviva for retrieving all events.
     */
    public void getAgenda(){
        String session = Credentials.getSession(c);
        String url = BASE_PATH + session +"/agenda";
        try {
            run(url, new Callback() {
                @Override public void onFailure(Call call, IOException e) {  }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String json = response.body().string();
                    if(!json.startsWith("[")){
                        //Set credentials from storage
                        ClassevivaCaller.this.mUser = Credentials.getName(c);
                        ClassevivaCaller.this.mPassword = Cripter.decriptString(Credentials.getPassword(c));

                        //Get a new session re-performing login
                        try {
                            newSession(ClassevivaCaller.class.getMethod("getAgenda", null));
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        }

                        return;
                    }
                    ArrayList<Evento> events = new ArrayList<>();

                    try {
                        JSONArray jsonArray = new JSONArray(json);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            String start = jsonObject.getString("start");
                            String dateToParse = start.substring(
                                    0,
                                    start.indexOf(" ")
                            );
                            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                            Date date = format.parse(dateToParse);
                            String title = jsonObject.getString("title");
                            String text = jsonObject.getString("nota_2");
                            String autore = jsonObject.getString("autore_desc");

                            Evento event = new Evento(date, title, text, autore);
                            events.add(event);
                        }


                        mCallback.onResponse(events);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call Classeviva login page with saved credentials to retrieve a new session
     */
    public void newSession(final Method method) {
        //Perform new HTTP call
        try {
            run(BASE_PATH + "PNIT0003/" + mUser + "/" + mPassword, new Callback() {
                @Override public void onFailure(Call call, IOException e) { }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String json = response.body().string();
                        JSONObject object = new JSONObject(json);

                        boolean success = object.getString("status").equals("OK") ? true : false;
                        if (success) Credentials.saveCredentials(c, mUser, mPassword, object.getString("sessionId"));

                        method.invoke(ClassevivaCaller.this);
                    }catch (JSONException e) { e.printStackTrace();  }
                    catch (InvocationTargetException e) { e.printStackTrace();  }
                    catch (IllegalAccessException e) { e.printStackTrace(); }
                }
            });
        } catch (IOException e) { e.printStackTrace(); }
    }

    String getMateria(String materia){

        //Replace the current String with something more "readable"
        if(materia.contains("(chimica)")) return "Chimica";
        else if(materia.contains("(fisica)")) return "Fisica";
        else if(materia.contains("(scienze ")) return "Scienze";
        else if(materia.contains("diritto")) return "Diritto";
        else if(materia.contains("lingua e")) return "Italiano";
        else if(materia.contains("lingua")) return "Inglese";
        else if(materia.contains("matematica")) return "Matematica";
        else if(materia.contains("rc")) return "Religione";
        else if(materia.contains("applicate")) return "Scienze Applicate";
        else if(materia.contains("scienze")) return "Ginnastica";
        else if(materia.contains("storia")) return "Storia";
        else if(materia.contains("tecnologie e")) return "Tecnica";
        else if(materia.contains("tecnologie") || materia.contains("informatica")) return "Informatica";
        else return materia;
    }
}