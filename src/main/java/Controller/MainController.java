package Controller;

import Model.BounceDefinition;
import Model.GraphPoint;
import Model.MainModel;
import View.CampaignTab;
import View.MainView;
import com.sun.javafx.charts.Legend;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.printing.Orientation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;


public class MainController {
    public static final int SLIDER_DAY = 0;
    public static final int SLIDER_WEEK = 1;
    public static final int SLIDER_MONTH = 2;
    public static final int SLIDER_YEAR = 3;


    private MainView view;
    private MainModel model;

    private String chartType = "Standard";

    private ArrayList<GraphPoint> graphData = new ArrayList<>();
    private boolean shouldGraphAvg = true; //Otherwise just sum
    private int timeGranulationValue = SLIDER_DAY;

    private File clickLogCSV;
    private File impressionLogCSV;
    private File serverLogCSV;

    @FXML
    LineChart<Number, Number> lineChart;

    @FXML
    TabPane tabPane;

    @FXML
    Tab defaultTab;

    @FXML
    Slider timeGranulationSlider;

    @FXML
    Button filterButton;
    @FXML
    Button filterRemoveButton;

    @FXML
    ListView filterListView;

    @FXML
    Button defineBounceButton;

    @FXML
    Button displayHistogramButton;

    @FXML
    CheckBox customBounceCheckBox;

    @FXML
    ComboBox chartTypeComboBox;

    public void initialize(){
        disableCampaignFunctionalityButtons();
        chartTypeComboBox.getItems().addAll("Standard","Per Hour of Day","Per Day of Week");
        chartTypeComboBox.getSelectionModel().select(0);
        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (ov, t, t1) -> {
                    if(t1 == defaultTab){
                        disableCampaignFunctionalityButtons();
                    }
                    else {
                        enableCampaignFunctionalityButtons();
                    }
                });
    }

    private void disableCampaignFunctionalityButtons(){
        customBounceCheckBox.setDisable(true);
        chartTypeComboBox.setDisable(true);
        timeGranulationSlider.setDisable(true);
        displayHistogramButton.setDisable(true);
        filterButton.setDisable(true);
        filterRemoveButton.setDisable(true);
    }

    private void enableCampaignFunctionalityButtons(){
        customBounceCheckBox.setDisable(false);
        chartTypeComboBox.setDisable(false);
        timeGranulationSlider.setDisable(false);
        displayHistogramButton.setDisable(false);
        filterButton.setDisable(false);
        filterRemoveButton.setDisable(false);
    }

    public void setView(MainView view){
        this.view = view;
    }

    public void setModel(MainModel model){
        this.model = model;
    }

    public void recreateGraph(int timeGranularityValue){
        timeGranulationValue = timeGranularityValue;
        NumberAxis xAxis = (NumberAxis) lineChart.getXAxis();
        xAxis.setLowerBound(1);
        lineChart.getData().clear();
        lineChart.getData().add(createSeries(timeGranularityValue));
        switch (timeGranularityValue){
            case SLIDER_DAY:
                xAxis.setUpperBound(graphData.size());
                xAxis.setTickUnit(1);
                xAxis.setLabel("Day of Campaign");
                return;
            case SLIDER_WEEK:
                xAxis.setUpperBound(Math.round(graphData.size()/7.0));
                xAxis.setTickUnit(1);
                xAxis.setLabel("Week of Campaign");
                return;
            case SLIDER_MONTH:
                xAxis.setUpperBound(Math.round(graphData.size()/30.0));
                xAxis.setTickUnit(1);
                xAxis.setLabel("Month of Campaign");
                return;
            case SLIDER_YEAR:
                xAxis.setUpperBound(Math.round(graphData.size()/365.0));
                xAxis.setTickUnit(1);
                xAxis.setLabel("Year of Campaign");
                return;
        }
    }

    private XYChart.Series<Number, Number> createSeries(int value){
        double divider = 1;
        switch (value){
            case SLIDER_DAY:
                divider = 1;
                break;
            case SLIDER_WEEK:
                divider = 7;
                break;
            case SLIDER_MONTH:
                divider = 30;
                break;
            case SLIDER_YEAR:
                divider = 365;
                break;
        }

        XYChart.Series<Number, Number> series = new XYChart.Series<>();

        int previousX = 0;
        int nextX;
        double holdTotal;
        double total = 0;
        double totalDenom = 0;

        for(GraphPoint point : graphData){
            nextX = (int) Math.floor(point.getX()/divider);
            if(previousX < nextX){
                holdTotal = shouldGraphAvg ? (totalDenom == 0 ? 0 : total/totalDenom) : (total);
                series.getData().add(new XYChart.Data<>(previousX+1, holdTotal));
                total = point.getYnum();
                totalDenom = point.getYdenom();
                previousX = nextX;
            }
            else {
                total += point.getYnum();
                totalDenom += point.getYdenom();
            }
        }
        holdTotal = shouldGraphAvg ? (totalDenom == 0 ? 0 : total/totalDenom) : (total);
        series.getData().add(new XYChart.Data<>(previousX+1, holdTotal));
        series.setName("Data");
        return series;
    }

    @FXML public void loadClickLogPressed(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        clickLogCSV = fileChooser.showOpenDialog(view.getWindow());
    }

    @FXML public void loadImpressionLogPressed(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        impressionLogCSV = fileChooser.showOpenDialog(view.getWindow());

    }

    @FXML public void loadServerLogPressed(){
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        serverLogCSV = fileChooser.showOpenDialog(view.getWindow());
    }

    @FXML public void loadCampaignPressed(){
        final String[] error = new String[1];
        if(clickLogCSV == null){
            view.showErrorMessage("Click Log file needed");
        }
        else if(impressionLogCSV == null){
            view.showErrorMessage("Impression Log file needed");
        }
        else if(serverLogCSV == null){
            view.showErrorMessage("Server Log file needed");
        }
        else if(!clickLogCSV.getName().endsWith(".csv")){
            view.showErrorMessage("Click Log file must be a CSV file");
        }
        else if(!impressionLogCSV.getName().endsWith(".csv")){
            view.showErrorMessage("Impression Log file must be a CSV file");
        }
        else if(!serverLogCSV.getName().endsWith(".csv")){
            view.showErrorMessage("Server Log file must be a CSV file");
        }
        else{
            Task task = new Task<ArrayList<CampaignTab.CampaignDataPackage>>() {
                @Override
                protected ArrayList<CampaignTab.CampaignDataPackage> call() {
                    error[0] = model.createNewCampaign(clickLogCSV,impressionLogCSV,serverLogCSV);
                    if(error[0] == null){
                        return model.queryOverallMetrics();
                    }
                    else {
                        return null;
                    }
                }
            };

            task.setOnRunning((e) -> {
                view.showLoadingDialog();
            });
            task.setOnSucceeded((e) -> {
                view.hideLoadingDialog();
                try {
                    if(error[0] == null) {
                        CampaignTab tab = new CampaignTab(this,((Task<ArrayList<CampaignTab.CampaignDataPackage>>) task).getValue());
                        clickLogCSV = null;
                        impressionLogCSV = null;
                        serverLogCSV = null;
                        tabPane.getTabs().add(tab);
                        tabPane.getSelectionModel().select(tab);
                    }
                    else {
                        view.showErrorMessage(error[0]);
                    }
                } catch (Exception e1) {
                    e1.printStackTrace();
                }
            });

            new Thread(task).start();
        }
    }

    public void metricSelectedOnCampaignTab(String metricSelected, String database){
        if(metricSelected == null){return;}

        shouldGraphAvg = !metricSelected.equals("Number of Impressions") && !metricSelected.equals("Number of Clicks") && !metricSelected.equals("Number of Uniques") && !metricSelected.equals("Number of Bounces") && !metricSelected.equals("Number of Conversions") && !metricSelected.equals("Total Cost");

        lineChart.getYAxis().setLabel(metricSelected);
        lineChart.setTitle(metricSelected + " Over Time");

        recreateGraph(timeGranulationValue);
    }

    public void updateGraphData(String metricSelected, String database){
        if(metricSelected == null){return;}
        graphData = model.queryCampaign(metricSelected);
    }

    @FXML public void addFilterButtonPressed() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/addFilterDialog.fxml"));
        Parent parent = fxmlLoader.load();
        Scene scene = new Scene(parent, 300, 300);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setScene(scene);
        AddFilterDialogController dialogController = fxmlLoader.getController();
        dialogController.init(model.getFilters());
        stage.showAndWait();
        if(!dialogController.isConfirmPressed()){
            return;
        }
        HashMap<String, List<String>> map = dialogController.getFilters();
        fillFilterListView(map);
        testUpdateCampaign(map);
    }

    @FXML public void removeFilterButtonPressed(){
        filterListView.getItems().clear();
        testUpdateCampaign(new HashMap<>());
    }

    private void fillFilterListView(HashMap<String, List<String>> map){
        filterListView.getItems().clear();
        for(String metric : map.keySet()){
            for(String filter : map.get(metric)) {
                filterListView.getItems().add(metric + " : " + filter);
            }
        }
    }

    @FXML
    public void onDisplayHistogramPressed() {
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(getClass().getResource("/histogram.fxml"));
        fxmlLoader.setController(new HistogramController(model));
//            HistogramController histogramController = (HistogramController) fxmlLoader.getController();
        try {

            Parent parent = fxmlLoader.load();
            Scene scene = new Scene(parent, 550, 450);
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(scene);
            stage.setResizable(false);
            stage.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();

        }
    }

    @FXML public void saveOrPrintSelected(){
        WritableImage image = lineChart.snapshot(new SnapshotParameters(), null);
        BufferedImage awtImage = SwingFXUtils.fromFXImage(image, null);

        PrinterJob printJob = PrinterJob.getPrinterJob();

        printJob.setPrintable((graphics, pageFormat, pageIndex) -> {
            int x = (int) Math.ceil(pageFormat.getImageableX());
            int y = (int) Math.ceil(pageFormat.getImageableY());
            if (pageIndex != 0) {
                return Printable.NO_SUCH_PAGE;
            }

            double scaler = Math.ceil(pageFormat.getImageableWidth())/awtImage.getWidth();

            graphics.drawImage(awtImage, x, y, (int) Math.ceil(pageFormat.getImageableWidth()), (int) Math.ceil(awtImage.getHeight()*scaler), null);
            return Printable.PAGE_EXISTS;
        });

        if (printJob.printDialog()) {
            try {
                printJob.print();
            } catch (PrinterException prt) {
                prt.printStackTrace();
            }
        }
    }

    @FXML
    public void customBounceCheckBoxSelected(){
        if(customBounceCheckBox.isSelected()){
            defineBounceButton.setDisable(false);
        }
        else {
            defineBounceButton.setDisable(true);
            model.setBounceAttributes(30,false);
            testUpdateCampaign(new HashMap<>());
        }
    }

    @FXML
    public void defineCustomBounceClicked() throws IOException{
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/setBounceDefinition.fxml"));
        Parent parent = fxmlLoader.load();
        SetBounceDefinitionDialogController controller = fxmlLoader.getController();
        Scene scene = new Scene(parent, 300, 150);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.showAndWait();

        if(controller.getIsConfirmPressed()) {
            model.setBounceAttributes(controller.getSecondsAfterEntry(), controller.getNeedToConvert());
            testUpdateCampaign(model.getFilters());
        }
    }

    @FXML
    public void onChartTypeComboBoxChanges(){
        String selected = (String) chartTypeComboBox.getSelectionModel().getSelectedItem();
        model.setChartType(selected);
        CampaignTab tab = (CampaignTab) tabPane.getTabs().get(1);

        Task task = new Task<Void>() {
            @Override
            protected Void call() {
                updateGraphData(tab.getSelected(),tab.getDatabaseID());
                return null;
            }
        };

        task = setBasicLoadingTaskMethods(task,tab);

        new Thread(task).start();
    }







    //TEST BUTTONS ONLY
    public void onTestButtonPressed(){
        HashMap map = new HashMap<String,List<String>>();
        List<String> contexts = new ArrayList<String>();
        contexts.add("Blog");
        //genders.add("Female");
        map.put("context",contexts);
        model.setBounceAttributes(50000,true);
        testUpdateCampaign(map);
    }

    public void testUpdateCampaign(HashMap<String,List<String>> map){
        CampaignTab tab = (CampaignTab) tabPane.getTabs().get(1);
        Task task = new Task<Void>() {
            @Override
            protected Void call() {
                model.setFilters(map);
                ArrayList<CampaignTab.CampaignDataPackage> list = model.queryOverallMetrics();
                tab.updateData(list);
                updateGraphData(tab.getSelected(),tab.getDatabaseID());
                return null;
            }
        };

        task = setBasicLoadingTaskMethods(task,tab);

        new Thread(task).start();


//        model.setFilters(map);
//        CampaignTab tab = (CampaignTab) tabPane.getTabs().get(1);
//
//
//        ArrayList<CampaignTab.CampaignDataPackage> list = model.queryOverallMetrics();
//        tab.updateData(list);
//        tab.retriggerSelectionProperty();

    }

    public Task<Void> setBasicLoadingTaskMethods(Task<Void> task, CampaignTab tab){
        task.setOnRunning((e) -> {
            view.showLoadingDialog();
        });

        task.setOnSucceeded((e) -> {
            view.hideLoadingDialog();
            metricSelectedOnCampaignTab(tab.getSelected(),tab.getDatabaseID());
        });
        return task;
    }

    @FXML
    public void useCurrentDatabase(){
        model.openCurrentDatabase();
        CampaignTab tab = new CampaignTab(this,model.queryOverallMetrics());
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
    }


}
