/*
* Copyright 2011 Google Inc. All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance  with the License.  
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/


package com.google.android.apps.paco;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 *
 */
public class ExploreDataActivity extends Activity {

  private Cursor cursor;
  private ExperimentProviderUtil experimentProviderUtil;
  private ListView list;
  private ViewGroup mainLayout;
  public UserPreferences userPrefs;
  private Experiment experiment;
  private List<Long> inputIds;
  private WebView webView = null;
  private Button rawDataButton;
  // Choices that have been selected on a multiselect list in a dialog.
  private HashMap<Long, List<Long>> checkedChoices = new HashMap<Long, List<Long>>();
  List<String> inpNames;
  boolean showDialog = true;
  
  
  
  
  
  ////////From Feedback Activity\\\\\\\\\\\
  private class Environment {

    
    private HashMap<String, String> map;

    public Environment(Map<String,String> map) {
      super();
      this.map = new HashMap<String, String>();
      this.map.putAll(map);
    }

    public String getValue(String key) {
      return map.get(key);
    }
    
  }
  ////////////////
  
  
  @Override
  //Make the first screen with which choices of what to do: Trends, Relationships, or Distributions (TRD)
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mainLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.explore_data, null);
    setContentView(mainLayout);
    
    //Check for experiments. If they do not exist, disable buttons and alert the user of that.
    experimentProviderUtil = new ExperimentProviderUtil(this);
    List<Experiment> experiments = experimentProviderUtil.getExperiments();
    if (experiments.size()<1){
      new AlertDialog.Builder(mainLayout.getContext()).setMessage("You haven't loaded any experiments yet, so this option does not make sense. Please come back after your have loaded an experiment and input data.").setCancelable(true).setPositiveButton("OK", new Dialog.OnClickListener() {

        public void onClick(DialogInterface dialog, int which) {
          dialog.dismiss();
        }
        
      }).create().show();
      Button chooseTrends = (Button)findViewById(R.id.TrendsButton);
      Button chooseRelationships = (Button)findViewById(R.id.RelationshipsButton);
      Button chooseDistributions = (Button)findViewById(R.id.DistributionsButton);
      chooseTrends.setEnabled(false);
      chooseRelationships.setEnabled(false);
      chooseDistributions.setEnabled(false);      
      return;
    }
    ////
    
    Button chooseTrends = (Button)findViewById(R.id.TrendsButton);
    chooseTrends.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        gotoVarSelection(1);
      }     
      });
    
    Button chooseRelationships = (Button)findViewById(R.id.RelationshipsButton);
    chooseRelationships.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        gotoVarSelection(2);
      }     
      });
    
    Button chooseDistributions = (Button)findViewById(R.id.DistributionsButton);
    chooseDistributions.setOnClickListener(new OnClickListener() {
      public void onClick(View v) {
        gotoVarSelection(3);
      }     
      });
    
  }
  
  //When we click on a TRD option, go to the next screen which is set up below with the options of which variables we want.
  //The screen will show you all of the experiments you are running. You can click on an experiment and decide
  //and a dialog will pop up allowing you to choose which variables you want to explore.
  //Then there is an OK button which will take you to the next screen or will tell you if you have an error.
  
  protected void gotoVarSelection(final int whichOption){
    mainLayout = (ViewGroup) getLayoutInflater().inflate(R.layout.variable_choices, null);
    setContentView(mainLayout);
    
    final Button varOkButton = (Button) findViewById(R.id.VarOkButton);
    varOkButton.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        analyzeData(checkedChoices, whichOption);
      }
    });
    
    Intent intent = getIntent();
    
    userPrefs = new UserPreferences(this);
    list = (ListView)findViewById(R.id.exploreable_experiments_list);
    experimentProviderUtil = new ExperimentProviderUtil(this);
    
    cursor = managedQuery(ExperimentColumns.JOINED_EXPERIMENTS_CONTENT_URI, 
        new String[] { ExperimentColumns._ID, ExperimentColumns.TITLE}, 
        null, null, null);
    
    SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, 
      android.R.layout.simple_list_item_2, cursor, 
      new String[] { ExperimentColumns.TITLE}, 
      new int[] { android.R.id.text1}) {
    };
    
      list.setAdapter(adapter);
      list.setOnItemClickListener(new OnItemClickListener() {
        
        @Override
        public void onItemClick(AdapterView<?> listview, View textview, int position,
            long id) {
          experiment = experimentProviderUtil.getExperiment(id);
          experimentProviderUtil.loadInputsForExperiment(experiment);
          
          if (experiment!= null) {
           inputIds = getInputIds(experiment.getInputs());
           inpNames = getInputNames(experiment.getInputs());
           renderMultiSelectListButton(id, (TextView) ((TwoLineListItem) textview).getChildAt(1));
           varOkButton.setVisibility(View.VISIBLE);
          }else{     Toast.makeText(ExploreDataActivity.this, "You didn't pick a proper experiment.",
            Toast.LENGTH_SHORT).show();}
        }
      });
  }
  
  //Make the dialog box containing variables in the experiment that is clicked on
  private View renderMultiSelectListButton(long ID, final TextView textview) {
    
    final Long id = ID;

    DialogInterface.OnMultiChoiceClickListener multiselectListDialogListener = new DialogInterface.OnMultiChoiceClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which, boolean isChecked) {
        if (isChecked){
          if (checkedChoices.get(id) !=null){
            checkedChoices.get(id).add(inputIds.get(which));
          }
          else{
            List<Long> tempList = new ArrayList<Long>();
            tempList.add(inputIds.get(which));
            checkedChoices.put(id, tempList);
          }
        }else{
          checkedChoices.get(id).remove(inputIds.get(which));
          if (checkedChoices.get(id).isEmpty())
            checkedChoices.remove(id);
        }
      }
    };

    AlertDialog.Builder builder = new AlertDialog.Builder(mainLayout.getContext());
    builder.setTitle("Make selections");

    boolean[] checkedChoicesBoolArray = new boolean[inputIds.size()];
    int count = inputIds.size();

    if (checkedChoices.get(id) !=null){
      for (int i = 0; i < count; i++) {
        checkedChoicesBoolArray[i] = checkedChoices.get(id).contains(inputIds.get(i));
      }
    }
    String[] listChoices = new String[inputIds.size()];
    inpNames.toArray(listChoices);
    builder.setMultiChoiceItems(listChoices, checkedChoicesBoolArray, multiselectListDialogListener);
    builder.setPositiveButton("OK",
      new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog,
              int whichButton) {
        String title = getExperiment(id).getTitle();
        getVarsText(id, textview);
        }
      });
    AlertDialog multiSelectListDialog = builder.create();
    multiSelectListDialog.show();
    return multiSelectListDialog.getListView();
  }
  
  private void getVarsText(long id, TextView textview){
    if (checkedChoices.get(id) !=null){
      String finalString = "  ";
      List<Long> tempVals;
      
        tempVals = checkedChoices.get(id);
        Experiment e = getExperiment(id);
        experimentProviderUtil.loadInputsForExperiment(e);
        for (Long val: tempVals){
          finalString+=(e.getInputById(val).getName()+"  ");
        }
      textview.setText(finalString);
    }else{
      textview.setText("");
    }
  }
  
  //When the OK button is hit, take the variables chosen and if the correct
  //number of variables are chosen, execute trends, relationships, or distributions
  //as indicated by the whichOpt variable
  private void analyzeData(HashMap<Long, List<Long>> choices, int whichOpt) {
    int choicesSize = 0;
    for (Long key:choices.keySet()){
      choicesSize+=choices.get(key).size();
    }
    if (whichOpt == 1){
          if (choicesSize==1){
            for (Long key:choices.keySet()){
            executeTrends(key, choices.get(key).get(0));}
          }else{
            chooseOneToast();
      }
   }else if(whichOpt == 2){
     if (choicesSize==2){
       if (choices.keySet().size()==1){//For data from the same experiment
         long varX, varY;
         for (Long key:choices.keySet()){
           varX = choices.get(key).get(0);
           varY = choices.get(key).get(1);
           executeRelationships(key, varX, varY);
         }
       }else if (choices.keySet().size()==2){//For data from two different experiments
         executeRelationships(choices);
       }
       }else{
         chooseTwoToast();
       }     
    } else if (whichOpt == 3){
      if (choicesSize==1){
        for (Long key:choices.keySet()){
        executeDistributions(key, choices.get(key).get(0));}
      }else{
        chooseOneToast();
      }
    }
  }

  //execute trends for one variable from one experiment
  private void executeTrends(Long expId, Long inpId) {
    experiment = getExperiment(expId);
    if (experiment == null) {
      Toast.makeText(ExploreDataActivity.this, "Experiment does not exist!",
        Toast.LENGTH_SHORT).show();
    } else {
      setContentView(R.layout.feedback);
      experimentProviderUtil.loadFeedbackForExperiment(experiment);
      experimentProviderUtil.loadInputsForExperiment(experiment);
      experimentProviderUtil.loadLatestEventForExperiment(experiment);
      final Feedback feedback = experiment.getFeedback().get(0);
      
      final Map<String,String> map = new HashMap<String, String>();      
      map.put("experimentalData", convertExperimentResultsToJsonString(feedback));
      map.put("title", experiment.getTitle());
      map.put("chosenVar", inpId+"");
      
      rawDataButton = (Button)findViewById(R.id.rawDataButton);
      rawDataButton.setVisibility(View.INVISIBLE);
      
      webView = (WebView)findViewById(R.id.feedbackText);
      webView.getSettings().setJavaScriptEnabled(true);
            
      final Environment env = new Environment(map);
      webView.addJavascriptInterface(env, "env");
      
      setWebChromeClientThatHandlesAlertsAsDialogs();
      WebViewClient webViewClient = createWebViewClientThatHandlesFileLinksForCharts(feedback);      
      webView.setWebViewClient(webViewClient);
      
      webView.loadUrl("file:///android_asset/straightToTime.html");
    }
  }

  //execute relationships for two variables from different experiments
  private void executeRelationships(HashMap<Long, List<Long>> keyValIds) {
    Long expXId, expYId, inpXId, inpYId;
    Object[] idArray;
    ArrayList<Long> temp = new ArrayList<Long>();
    
    for (Long key:keyValIds.keySet()){
      temp.add(key);
      temp.add(keyValIds.get(key).get(0));
    }
    
    idArray = temp.toArray();
    expXId = (Long) idArray[0]; inpXId = (Long) idArray[1]; expYId = (Long) idArray[2]; inpYId = (Long) idArray[3];
    
    experiment = getExperiment(expXId);
    final Map<String,String> map = new HashMap<String, String>();

    if (experiment == null) {
      Toast.makeText(ExploreDataActivity.this, "Experiment does not exist!",
        Toast.LENGTH_SHORT).show();
    } else {
      map.put("expXId", expXId+"");
      map.put("chosenVarX", inpXId+"");
    }
    
    experiment = getExperiment(expYId);
    if (experiment == null) {
      Toast.makeText(ExploreDataActivity.this, "Experiment does not exist!",
        Toast.LENGTH_SHORT).show();
    } else {
      setContentView(R.layout.feedback);
      map.put("expYId", expYId+"");
      map.put("chosenVarY", inpYId+"");
      
      rawDataButton = (Button)findViewById(R.id.rawDataButton);
      rawDataButton.setVisibility(View.INVISIBLE);
      
      webView = (WebView)findViewById(R.id.feedbackText);
      webView.getSettings().setJavaScriptEnabled(true);

      final Environment env = new Environment(map);
      webView.addJavascriptInterface(env, "env");
      setWebChromeClientThatHandlesAlertsAsDialogs();
      
      webView.loadUrl("file:///android_asset/relationshipsDifferentExperiments.html");
    }
  }
  
  //execute relationships for two variables within the same experiment
  private void executeRelationships(Long expId, long inpX, long inpY) {
    experiment = getExperiment(expId);
    if (experiment == null) {
      Toast.makeText(ExploreDataActivity.this, "Experiment does not exist!",
        Toast.LENGTH_SHORT).show();
    } else {
      setContentView(R.layout.feedback);
      experimentProviderUtil.loadFeedbackForExperiment(experiment);
      experimentProviderUtil.loadInputsForExperiment(experiment);
      experimentProviderUtil.loadLatestEventForExperiment(experiment);
      final Feedback feedback = experiment.getFeedback().get(0);
      
      final Map<String,String> map = new HashMap<String, String>();      
      map.put("experimentalData", convertExperimentResultsToJsonString(feedback));
      map.put("title", experiment.getTitle());
      map.put("chosenVarX", inpX+"");
      map.put("chosenVarY", inpY+"");
      
      rawDataButton = (Button)findViewById(R.id.rawDataButton);
      rawDataButton.setVisibility(View.INVISIBLE);
      
      webView = (WebView)findViewById(R.id.feedbackText);
      webView.getSettings().setJavaScriptEnabled(true);

      final Environment env = new Environment(map);
      webView.addJavascriptInterface(env, "env");
      
      setWebChromeClientThatHandlesAlertsAsDialogs();
      WebViewClient webViewClient = createWebViewClientThatHandlesFileLinksForCharts(feedback);      
      webView.setWebViewClient(webViewClient);
      
      webView.loadUrl("file:///android_asset/relationships.html");
    }
  }
  
  //execute distributions for one experiment
  private void executeDistributions(Long expId, Long inpId) {
    experiment = getExperiment(expId);
    if (experiment == null) {
      Toast.makeText(ExploreDataActivity.this, "Experiment does not exist!",
        Toast.LENGTH_SHORT).show();
    } else {
      setContentView(R.layout.feedback);
      experimentProviderUtil.loadFeedbackForExperiment(experiment);
      experimentProviderUtil.loadInputsForExperiment(experiment);
      experimentProviderUtil.loadLatestEventForExperiment(experiment);
      final Feedback feedback = experiment.getFeedback().get(0);
      
      final Map<String,String> map = new HashMap<String, String>();      
      map.put("experimentalData", convertExperimentResultsToJsonString(feedback));
      map.put("title", experiment.getTitle());
      map.put("chosenVar", inpId+"");
      
      rawDataButton = (Button)findViewById(R.id.rawDataButton);
      rawDataButton.setVisibility(View.INVISIBLE);
      
      webView = (WebView)findViewById(R.id.feedbackText);
      webView.getSettings().setJavaScriptEnabled(true);

      final Environment env = new Environment(map);
      webView.addJavascriptInterface(env, "env");
      
      setWebChromeClientThatHandlesAlertsAsDialogs();
      WebViewClient webViewClient = createWebViewClientThatHandlesFileLinksForCharts(feedback);      
      webView.setWebViewClient(webViewClient);
      
      webView.loadUrl("file:///android_asset/distributions.html");
    }
  }
  
  @Override
  public void onConfigurationChanged(Configuration newConfig){
    Toast.makeText(ExploreDataActivity.this, checkedChoices.toString(),
      Toast.LENGTH_SHORT).show();
    
      super.onConfigurationChanged(newConfig);
  }
  
  public void chooseOneToast(){
  Toast.makeText(ExploreDataActivity.this, "Sorry, please select exactly one variable.",
    Toast.LENGTH_SHORT).show();
  }
  
  public void chooseTwoToast(){
    Toast.makeText(ExploreDataActivity.this, "Sorry, please select exactly two variables.",
      Toast.LENGTH_SHORT).show();
    }
  private List<String> getInputNames(List<Input> i){
    List<String> tempInputNames = new ArrayList<String>();
    for (Input inp: i){
      tempInputNames.add(inp.getName());
    }
    return tempInputNames;
  }
  
  private List<Long> getInputIds(List<Input> inputs){
    List<Long> tempIds = new ArrayList<Long>();
    for (Input inp: inputs){
      tempIds.add(inp.getServerId());
    }
    return tempIds;
  }
  
  
/*  private String getNamesOfSelectedVariables(HashMap<Long, List<Long>> checked) {
    String finalString = "  ";
    List<Long> tempVals;
    for (Long experimentId: checked.keySet()){
      tempVals = checked.get(experimentId);
      Experiment e = getExperiment(experimentId);
      experimentProviderUtil.loadInputsForExperiment(e);
      for (Long val: tempVals){
        finalString+=(e.getInputById(val).getName()+"  ");
      }
    }
    
    return finalString;
  }*/
  
  private Experiment getExperiment(long expId){
    return experimentProviderUtil.getExperiment(expId);
  }
  
///////Copied from FeedbackActivity\\\\\\\\\\\\
  private String convertExperimentResultsToJsonString(final Feedback feedback) {
    final JSONArray experimentData = new JSONArray();
    for (Event event : experiment.getEvents()) {
      try {
        JSONObject eventObject = new JSONObject();
        boolean missed = event.getResponseTime() == null;
        eventObject.put("isMissedSignal", missed);
        if (!missed) {
          eventObject.put("responseTime", event.getResponseTime().getMillis());
        }
        
        boolean selfReport = event.getScheduledTime() == null;
        eventObject.put("isSelfReport", selfReport);
        if (!selfReport) {
          eventObject.put("scheduleTime", event.getScheduledTime().getMillis());
        }
        
        
        
        JSONArray responses = new JSONArray();
        for (Output response : event.getResponses()) {
          JSONObject responseJson = new JSONObject();
          Input input = experiment.getInputById(response.getInputServerId());     
          if (input == null) {
            continue;
          }
          responseJson.put("inputId", input.getServerId());
          responseJson.put("inputName", input.getName());
          responseJson.put("responseType", input.getResponseType());
          responseJson.put("prompt", feedback.getTextOfInputForOutput(experiment, response));
          responseJson.put("answer", feedback.getDisplayOfAnswer(response, input));
          responseJson.put("answerOrder", response.getAnswer());  
          responses.put(responseJson);
        }          
        
        eventObject.put("responses", responses);
        if (responses.length() > 0) {
          experimentData.put(eventObject);
        }
      } catch (JSONException jse) {
        // skip this event and do the next event. 
      }
    }
    String experimentDataAsJson = experimentData.toString();
    return experimentDataAsJson;
  }
  
  
  private String convertSingleExperimentResultsToJsonString(final Feedback feedback, String rName) {
    final JSONArray experimentData = new JSONArray();
    for (Event event : experiment.getEvents()) {
      try {
        JSONObject eventObject = new JSONObject();
        boolean missed = event.getResponseTime() == null;
        eventObject.put("isMissedSignal", missed);
        if (!missed) {
          eventObject.put("responseTime", event.getResponseTime().getMillis());
        }
        
        boolean selfReport = event.getScheduledTime() == null;
        eventObject.put("isSelfReport", selfReport);
        if (!selfReport) {
          eventObject.put("scheduleTime", event.getScheduledTime().getMillis());
        }
        
        
        
        JSONArray responses = new JSONArray();
        for (Output response : event.getResponses()) {
            JSONObject responseJson = new JSONObject();
            Input input = experiment.getInputById(response.getInputServerId());     
            if (input == null) {
              continue;
            }
            responseJson.put("inputId", input.getServerId());
            responseJson.put("inputName", input.getName());
            responseJson.put("responseType", input.getResponseType());
            responseJson.put("prompt", feedback.getTextOfInputForOutput(experiment, response));
            responseJson.put("answer", feedback.getDisplayOfAnswer(response, input));
            responseJson.put("answerOrder", response.getAnswer());  
            responses.put(responseJson);
          }
        eventObject.put("responses", responses);
        if (responses.length() > 0) {
          experimentData.put(eventObject);
        }
      } catch (JSONException jse) {
        // skip this event and do the next event. 
      }
    }
    String experimentDataAsJson = experimentData.toString();
    return experimentDataAsJson;
  }
  
  
  private void setWebChromeClientThatHandlesAlertsAsDialogs() {
    webView.setWebChromeClient(new WebChromeClient() {
      @Override
      public boolean onJsAlert(WebView view, String url, String message, JsResult result) {

        new AlertDialog.Builder(view.getContext()).setMessage(message).setCancelable(true).setPositiveButton("OK", new Dialog.OnClickListener() {

          public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
          }
          
        }).create().show();
        result.confirm();
        return true;
      }
      
      public boolean onJsConfirm (WebView view, String url, String message, final JsResult result){
        if (url.contains("file:///android_asset/map.html")){
          if (showDialog == false){
            result.confirm();
            return true;
          } else{
            new AlertDialog.Builder(view.getContext()).setMessage(message).setCancelable(true).setPositiveButton("OK", new Dialog.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                showDialog = false;
                dialog.dismiss();
                result.confirm();
              }
            }).setNegativeButton("Cancel", new Dialog.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                result.cancel();
              } 
            }).create().show();
            return true;
          }
        }
        return super.onJsConfirm(view, url, message, result);
      }
    });
  }
  
  private WebViewClient createWebViewClientThatHandlesFileLinksForCharts(final Feedback feedback) {
    WebViewClient webViewClient = new WebViewClient() {

      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        Uri uri = Uri.parse(url);
        if (uri.getScheme().startsWith("http")) {
          return false;
        }
        
        String inputIdStr = uri.getQueryParameter("inputId");
        long inputId = Long.valueOf(inputIdStr);
        JSONArray results = new JSONArray();
        for (Event event : experiment.getEvents()) {
          JSONArray eventJson = new JSONArray();
          DateTime responseTime = event.getResponseTime();
          if (responseTime == null) {
            continue; // missed signal;
          }
          eventJson.put(responseTime.getMillis());
          
          // in this case we are looking for one input from the responses that we are charting.
          for (Output response : event.getResponses()) {
            if (response.getInputServerId() == inputId ) {
              Input inputById = experiment.getInputById(inputId);
              if (!inputById.isInvisible() && inputById.isNumeric()) {               
                eventJson.put(feedback.getDisplayOfAnswer(response, 
                    inputById));
                results.put(eventJson);
                continue;
              }
            }
          }
          
        }
        Map<String, String> map2 = new HashMap();
        Environment chartEnv = new Environment(map2);
        map2.put("data", results.toString());
        
        view.addJavascriptInterface(chartEnv, "chartEnv");
        view.loadUrl(url);
        return true;
      }
      
    };
    return webViewClient;
  }
  ////////////
}