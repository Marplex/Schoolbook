package com.marco.marplex.schoolbook.connections;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.marco.marplex.schoolbook.interfaces.ClassevivaCallback;
import com.marco.marplex.schoolbook.interfaces.ClassevivaLoginCallback;
import com.marco.marplex.schoolbook.models.Comunication;
import com.marco.marplex.schoolbook.models.Evento;
import com.marco.marplex.schoolbook.models.Note;
import com.marco.marplex.schoolbook.models.Voto;
import com.marco.marplex.schoolbook.utilities.Credentials;
import com.marco.marplex.schoolbook.utilities.Cripter;
import com.marco.marplex.schoolbook.utilities.SharedPreferences;

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
import java.util.Iterator;
import java.util.TreeSet;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static android.R.attr.id;

/**
 * Created by marco on 1/27/16.
 */
public class ClassevivaCaller {

    private final String BASE_PATH = "http://schoolbook.cloudno.de/";
    private final String BASE_PATH2 = "https://shielded-brushlands-74018.herokuapp.com/";

    private String CURRENT_BASE_PATH;
    protected final String TAG = "Classeviva Login";

    ClassevivaLoginCallback mClassevivaLoginCallback;
    ClassevivaCallback mCallback;

    String mUser, mPassword;
    Context c;

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
        this.CURRENT_BASE_PATH = BASE_PATH;
        this.client = new OkHttpClient();
    }

    public ClassevivaCaller(ClassevivaCallback callback, Context context) {
        this.mCallback = callback;
        this.mUser = Credentials.getName(context);
        this.mPassword = Credentials.getPassword(context);
        this.client = new OkHttpClient();
        this.c = context;
        this.CURRENT_BASE_PATH = BASE_PATH;
    }

    private void run(String url, final Method fromWhere, final EndpointsCallback callback) throws IOException {
        Log.d(TAG, "run: "+url);
        final Request request = new Request.Builder()
                .url(url)
                .build();

        Callback switcherCallback = new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();

                //If one server goes offline, use another one.
                try {
                    if (CURRENT_BASE_PATH.equals(BASE_PATH)) {
                        if (body.startsWith("<") && !body.contains(".btn_confirm")) {
                            CURRENT_BASE_PATH = BASE_PATH2;
                            fromWhere.invoke(ClassevivaCaller.this);
                        } else callback.onResponse(body);
                    } else {
                        if (body.startsWith("<") && !body.contains(".btn_confirm")) {
                            CURRENT_BASE_PATH = BASE_PATH;
                            fromWhere.invoke(ClassevivaCaller.this);
                        } else callback.onResponse(body);
                    }
                }catch(Exception e){}
            }
        };

        client.newCall(request).enqueue(switcherCallback);
    }

    /**
     * Call Classeviva login page with saved or requested credentials
     */
    public void doLogin() {
        //Perform new HTTP call
        try {
            run(CURRENT_BASE_PATH + "PNIT0003/" + mUser + "/" + mPassword, ClassevivaCaller.class.getMethod("doLogin", null),
                    new EndpointsCallback() {
                @Override
                public void onResponse(String json) {
                    try {
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
        } catch (NoSuchMethodException e) {
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
            run(CURRENT_BASE_PATH + session + "/votes", ClassevivaCaller.class.getMethod("getVotes", null),
                    new EndpointsCallback() {
                @Override public void onResponse(String json){
                    try {
                        Log.d(TAG, "onResponse: "+json);
                        if( json.equals("{}") || json.startsWith("<") ){
                            //Set credentials from storage
                            ClassevivaCaller.this.mUser = Credentials.getName(c);
                            ClassevivaCaller.this.mPassword = Cripter.decriptString(Credentials.getPassword(c));

                            //Get a new session re-performing login
                            Log.d(TAG, "onResponse: new SESSION from Votes");
                            newSession(ClassevivaCaller.class.getMethod("getVotes", null));
                            return;
                        }


                        ArrayList<String> subjects = new ArrayList<>();
                        ArrayList<Voto> votes = new ArrayList<>();
                        JSONObject object = new JSONObject(json){
                            @Override
                            public Iterator keys(){
                                TreeSet<Object> sortedKeys = new TreeSet<Object>();
                                Iterator keys = super.keys();
                                while(keys.hasNext()){
                                    sortedKeys.add(keys.next());
                                }
                                return sortedKeys.iterator();
                            }
                        };

                        Iterator<String> keysIterator = object.keys();
                        while (keysIterator.hasNext())
                        {
                            String materia = keysIterator.next();
                            subjects.add(getMateria(materia));
                            JSONArray valueObject = object.getJSONArray(materia);

                            for(int x = 0; x<valueObject.length(); x++){
                                JSONObject voteObject = valueObject.getJSONObject(x);
                                String voto = voteObject.getString("vote");
                                String date = voteObject.getString("date");
                                String type = voteObject.getString("type");
                                int period = voteObject.getInt("period");
                                Voto vote = new Voto(voto, getMateria(materia), date, type, period);
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
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Make a request to classeviva for retrieving all events.
     */
    public void getAgenda(){
        String session = Credentials.getSession(c);
        String url = CURRENT_BASE_PATH + session +"/agenda";
        try {
            run(url, ClassevivaCaller.class.getMethod("getAgenda", null), new EndpointsCallback() {
                @Override
                public void onResponse(String json){
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
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Make a request to classeviva for retrieving all school's comunication.
     */
    public void getSchoolComunication(){
        String session = Credentials.getSession(c);
        String url = CURRENT_BASE_PATH + session +"/circolari";
        try {
            run(url, ClassevivaCaller.class.getMethod("getSchoolComunication", null), new EndpointsCallback() {
                @Override
                public void onResponse(String json){
                    if(json.equals("[]")){
                        //Set credentials from storage
                        ClassevivaCaller.this.mUser = Credentials.getName(c);
                        ClassevivaCaller.this.mPassword = Cripter.decriptString(Credentials.getPassword(c));

                        //Get a new session re-performing login
                        try {
                            newSession(ClassevivaCaller.class.getMethod("getSchoolComunication", null));
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        }

                        return;
                    }
                    ArrayList<Comunication> comunications = new ArrayList<>();

                    try {
                        JSONArray jsonArray = new JSONArray(json);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);

                            int id = jsonObject.getInt("id");
                            String date = jsonObject.getString("date");
                            String title = jsonObject.getString("title").replaceAll("\n","");
                            String link = jsonObject.getString("url");

                            comunications.add(new Comunication(id, title, date, link));
                        }

                        mCallback.onResponse(comunications);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Make a request to classeviva for retrieving all user's notes.
     */
    public void getNotes(){
        String session = Credentials.getSession(c);
        String url = CURRENT_BASE_PATH + session +"/note";
        try {
            run(url, ClassevivaCaller.class.getMethod("getNotes", null), new EndpointsCallback() {
                @Override
                public void onResponse(String json){
                    if(json.equals("[]")){
                        //Set credentials from storage
                        ClassevivaCaller.this.mUser = Credentials.getName(c);
                        ClassevivaCaller.this.mPassword = Cripter.decriptString(Credentials.getPassword(c));

                        //Get a new session re-performing login
                        try {
                            newSession(ClassevivaCaller.class.getMethod("getNotes", null));
                        } catch (NoSuchMethodException e) {
                            e.printStackTrace();
                        }

                        return;
                    }
                    ArrayList<Note> notes = new ArrayList<>();

                    try {
                        JSONArray jsonArray = new JSONArray(json);
                        for (int i = 0; i < jsonArray.length(); i++) {
                            JSONObject jsonObject = jsonArray.getJSONObject(i);

                            String date = jsonObject.getString("date").replaceAll("\n","").replaceAll("-", "/");
                            String prof = jsonObject.getString("prof").replaceAll("\n","");
                            String note = jsonObject.getString("note").replaceAll("\n","");
                            String type = jsonObject.getString("type").replaceAll("\n","");

                            notes.add(new Note(prof, date, type, note));
                        }

                        mCallback.onResponse(notes);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    /**
     * Call Classeviva login page with saved credentials to retrieve a new session
     */
    public void newSession(final Method method) {
        //Perform new HTTP call
        try {
            run(CURRENT_BASE_PATH + "PNIT0003/" + mUser + "/" + mPassword, ClassevivaCaller.class.getMethod("newSession", method.getClass()),
                    new EndpointsCallback() {
                @Override
                public void onResponse(String json){
                    try {
                        JSONObject object = new JSONObject(json);

                        boolean success = object.getString("status").equals("OK") ? true : false;
                        if (success) Credentials.saveCredentials(c, mUser, mPassword, object.getString("sessionId"));

                        method.invoke(ClassevivaCaller.this);
                    }catch (JSONException e) { e.printStackTrace();  }
                    catch (InvocationTargetException e) { e.printStackTrace();  }
                    catch (IllegalAccessException e) { e.printStackTrace(); }
                }
            });
        } catch (IOException e) { e.printStackTrace(); } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
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

    private interface EndpointsCallback{
        void onResponse(String json);
    }
}