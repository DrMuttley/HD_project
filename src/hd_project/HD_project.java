/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package hd_project;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.poi.hssf.usermodel.HSSFFont;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;

/**
 * Main class of application
 * 
 * @author Adam Wojnarowski, Łukasz Nowak
 * @version 1.0
 */
public class HD_project {

    /**
     * Main method of application - manages the flow of application control
     * @param args command line arguments
     */
    public static void main(String[] args) {

        HD_project hd_project = new HD_project();

        //uploading data form files: nozzleMeasures.log, refuel.log, 
        //tankMeasures.log to database
        /*
        hd_project.addAllRefuelToDB(hd_project);
        hd_project.addAllTankMeasuresToDB(hd_project);    
        hd_project.addAllNozzleMeasuresToDB(hd_project);
        */
        
        //preparing file for output data
        hd_project.prepareExcelFileForData("outputData.xls");
        
        //number of first nozzle in first tankID
        Integer nozzleNo = 13;
        
        //primary or distorted data type           
        String dataType = "PIERWOTNE";
        
        //loop for primary and distorted data
        for (int dType = 0; dType < 2; dType++) {

            //loop for tankID regulation - from 1 to 5
            for (int tankID = 1; tankID < 5; tankID++) {

                List<Timestamp> timeStampList = new LinkedList<>();

                List<Double> averageTankHeightList = new LinkedList<>();
                List<Double> averageTankVolumeList = new LinkedList<>();

                List<Double> averageNozzleVolumeList = new LinkedList<>();

                //loop for sets regulation - from 1 to 3
                for (int setNO = 1; setNO < 2; setNO++) {

                    List<Double> heightSingleSetList = new LinkedList<>();
                    List<Double> volumeSingleSetList = new LinkedList<>();

                    //loading data from tankMeasures - single set
                    hd_project.selectDataFromTankMeasures(dataType, tankID, setNO, timeStampList,
                            heightSingleSetList, volumeSingleSetList);

                    hd_project.addToAverageList(averageTankHeightList, heightSingleSetList);
                    hd_project.addToAverageList(averageTankVolumeList, volumeSingleSetList);

                    //loading data from nozzleMeasures - signel set
                    List<Double> totalCounterNozzleOneList = new LinkedList<>();
                    List<Double> totalCounterNozzleTwoList = new LinkedList<>();
                    List<Double> totalCounterNozzleThreeList = new LinkedList<>();

                    hd_project.selectDataFromNozzleMeasures(dataType, tankID, nozzleNo, setNO, totalCounterNozzleOneList);
                    hd_project.selectDataFromNozzleMeasures(dataType, tankID, nozzleNo + 4, setNO, totalCounterNozzleTwoList);
                    hd_project.selectDataFromNozzleMeasures(dataType, tankID, nozzleNo + 8, setNO, totalCounterNozzleThreeList);

                    List<Double> nozzlesSumList = hd_project.sumList(totalCounterNozzleOneList,
                            totalCounterNozzleTwoList, totalCounterNozzleThreeList);

                    //loading data from refuel - single set
                    List<Timestamp> timeStampRefuelList = new LinkedList<>();
                    List<Double> volumeRefuelList = new LinkedList<>();

                    hd_project.selectDataFromRefuel(dataType, tankID, setNO, timeStampRefuelList, volumeRefuelList);

                    List<Double> addFuelList = new LinkedList<>();

                    //checking which type of data from refuel
                    if (setNO < 3) {
                        addFuelList = hd_project.addFuelFromRefuelOneTwo(timeStampList,
                                timeStampRefuelList, volumeRefuelList);
                    } else {
                        addFuelList = hd_project.addFuelFromRefuelThree(timeStampList,
                                timeStampRefuelList, volumeRefuelList);
                    }

                    hd_project.moveForAnHour(addFuelList);

                    //connecting data from nozzleMeasures with refuel
                    List<Double> volumeNozzleWithRefuel = new LinkedList<>();

                    volumeNozzleWithRefuel = hd_project.calculateVolume(averageTankVolumeList,
                            nozzlesSumList, addFuelList);

                    hd_project.addToAverageList(averageNozzleVolumeList, volumeNozzleWithRefuel);
                }

                hd_project.saveDataToExcelFile(dataType, tankID, averageTankHeightList, averageTankVolumeList,
                        averageNozzleVolumeList);
                
                nozzleNo++;
            }
            dataType = "ZNIEKSZTALCONE";
            nozzleNo = 13;
        }
    }
    
    /***************************************************************************
     ****************** LOADING DATA FROM FILES TO DATABASE ********************
     ***************************************************************************/ 
    
    /**
     * Method creates table in database, based on creatingTableString and tableName
     * @param creatingTableString - string creating table
     * @param tableName - string with table name
     */
    private void createTable(String creatingTableString, String tableName) {

        Connection con = null;
        try { 
            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527/hd_project", "root", "root");

            Statement statement = con.createStatement();

            statement.executeUpdate(creatingTableString);
            System.out.println(tableName + " - table created");
        } catch (SQLException sqle) {
            System.err.println("SQL exception: " + sqle.getMessage());
        } catch (ClassNotFoundException cnfe) {
            System.err.println("ClassNotFound exception: " + cnfe.getMessage());
        } finally {
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method creates tables and manages adding data from a file nozzleMeasures.log
     * @param hd_project - class object
     */
    private void addAllNozzleMeasuresToDB(HD_project hd_project) {

        String type = "pierwotne";

        for (int i = 0; i < 2; i++) {

            for (int j = 1; j < 4; j++) {

                String nozzleMeasuresName = type + "_ZESTAW_" + Integer.toString(j) + "_NOZZLE_MEASURES";
                String nozzleMeasuresPartOfPath = type + "\\Zestaw " + Integer.toString(j);

                String nozzleMeasures = "CREATE TABLE " + nozzleMeasuresName + " ("
                        + "id INTEGER NOT NULL "
                        + "PRIMARY KEY GENERATED ALWAYS AS IDENTITY "
                        + "(START WITH 1, INCREMENT BY 1), "
                        + "timestamp TIMESTAMP, "
                        + "nozzle_id INTEGER, "
                        + "tank_id INTEGER, "
                        + "liter_counter DOUBLE,"
                        + "total_counter DOUBLE, "
                        + "nozzle_status INTEGER)";

                hd_project.createTable(nozzleMeasures, nozzleMeasuresName);

                hd_project.readDataFromNozzleMeasuresFiles(nozzleMeasuresName, nozzleMeasuresPartOfPath);

                System.out.println(nozzleMeasuresName + " - data inserted");
            }
            type = "znieksztalcone";
        }
    }

    /**
     * Method creates tables and manages adding data from a file tankMeasures.log
     * @param hd_project - class object
     */
    private void addAllTankMeasuresToDB(HD_project hd_project) {

        String type = "pierwotne";

        for (int i = 0; i < 2; i++) {

            for (int j = 1; j < 4; j++) {

                String tankMeasuresName = type + "_ZESTAW_" + Integer.toString(j) + "_TANK_MEASURES";
                String tankMeasuresPartOfPath = type + "\\Zestaw " + Integer.toString(j);

                String tankMeasures = "CREATE TABLE " + tankMeasuresName + " ("
                        + "id INTEGER NOT NULL "
                        + "PRIMARY KEY GENERATED ALWAYS AS IDENTITY "
                        + "(START WITH 1, INCREMENT BY 1), "
                        + "timestamp TIMESTAMP, "
                        + "tank_id INTEGER, "
                        + "fuel_height DOUBLE, "
                        + "fuel_volume DOUBLE, "
                        + "temperature INTEGER)";

                hd_project.createTable(tankMeasures, tankMeasuresName);

                hd_project.readDataFromTankMeasuresFiles(tankMeasuresName, tankMeasuresPartOfPath);

                System.out.println(tankMeasuresName + " - data inserted");
            }
            type = "znieksztalcone";
        }
    }

    /**
     * Method creates tables and manages adding data from a file refuel.log
     * @param hd_project - class object
     */
    private void addAllRefuelToDB(HD_project hd_project) {

        String type = "pierwotne";

        for (int i = 0; i < 2; i++) {

            for (int j = 1; j < 4; j++) {

                String refuelName = type + "_ZESTAW_" + Integer.toString(j) + "_REFUEL";
                String refuelPartOfPath = type + "\\Zestaw " + Integer.toString(j);

                String refuel = "CREATE TABLE " + refuelName + " ("
                        + "id INTEGER NOT NULL "
                        + "PRIMARY KEY GENERATED ALWAYS AS IDENTITY "
                        + "(START WITH 1, INCREMENT BY 1), "
                        + "timestamp TIMESTAMP, "
                        + "tank_id INTEGER, "
                        + "fuel_volume DOUBLE, "
                        + "tank_speed DOUBLE)";

                hd_project.createTable(refuel, refuelName);

                hd_project.readDataFromRefuelFiles(refuelName, refuelPartOfPath);

                System.out.println(refuelName + " - data inserted");
            }
            type = "znieksztalcone";
        }
    }

    /**
     * Method reads data from nozzleMeasures.log
     * @param nozzleMeasuresName - string with nozzleMeasures table name
     * @param nozzleMeasurePartOfPath - string with part of path to nozzleMeasures.log
     */    
    private void readDataFromNozzleMeasuresFiles(String nozzleMeasuresName, 
            String nozzleMeasurePartOfPath) {

        String path = "Dane paliwowe\\dane\\" + nozzleMeasurePartOfPath + "\\nozzleMeasures.log";

        BufferedReader br = loadFileWithData(path);

        String line;

        Connection con = null;

        try {

            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527"
                    + "/hd_project", "root", "root");

            while ((line = br.readLine()) != null) {
                saveToNozzleMeasures(prepareSingleRow(line), nozzleMeasuresName, con);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {

            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method reads data from tankMeasures.log
     * @param tankMeasuresName - string with tankMeasures table name 
     * @param tankMeasurePartOfPath - string with part of path to tankMeasures.log
     */
    private void readDataFromTankMeasuresFiles(String tankMeasuresName, 
            String tankMeasurePartOfPath) {

        String path = "Dane paliwowe\\dane\\" + tankMeasurePartOfPath + "\\tankMeasures.log";

        BufferedReader br = loadFileWithData(path);

        String line;

        Connection con = null;

        try {

            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527"
                    + "/hd_project", "root", "root");

            while ((line = br.readLine()) != null) {
                saveToTankMeasures(prepareSingleRow(line), tankMeasuresName, con);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {

            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method reads data from refuel.log
     * @param refuelName - string with refuel table name
     * @param refuelPartOfPath - string with part of path to refuel.log
     */
    private void readDataFromRefuelFiles(String refuelName, String refuelPartOfPath) {

        String path = "Dane paliwowe\\dane\\" + refuelPartOfPath + "\\refuel.log";

        BufferedReader br = loadFileWithData(path);

        String line;

        Connection con = null;

        try {

            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527"
                    + "/hd_project", "root", "root");

            while ((line = br.readLine()) != null) {
                saveToRefuel(prepareSingleRow(line), refuelName, con);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {

            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method adds data from nozzleMeasures.log to database
     * @param singleRow - list with data from nozzleMeasures.log - single row
     * @param nozzleMeasuresName - string with nozzleMeasures table name
     * @param con - connection object
     * @throws ClassNotFoundException
     * @throws SQLException 
     */
    private void saveToNozzleMeasures(List<String> singleRow, String nozzleMeasuresName,
            Connection con) throws ClassNotFoundException, SQLException {

        Statement statement = con.createStatement();

        statement.executeUpdate("INSERT INTO " + nozzleMeasuresName + " (timestamp, "
                + "nozzle_id, tank_id, liter_counter, total_counter, nozzle_status) VALUES ('"
                + Timestamp.valueOf(singleRow.get(0)) + "', "
                + Integer.parseInt(singleRow.get(2)) + ", "
                + Integer.parseInt(singleRow.get(3)) + ", "
                + Double.parseDouble(singleRow.get(4).replace(",", ".")) + ", "
                + Double.parseDouble(singleRow.get(5).replace(",", ".")) + ", "
                + Integer.parseInt(singleRow.get(6))
                + " )");
        //System.out.println("Data inserted");
    }

    /**
     * Method adds data from tankMeasures.log to database
     * @param singleRow - list with data from tankMeasures.log - single row
     * @param tankMeasuresName - string with tankMeasures table name
     * @param con - connection object
     * @throws ClassNotFoundException
     * @throws SQLException 
     */
    private void saveToTankMeasures(List<String> singleRow, String tankMeasuresName,
            Connection con) throws ClassNotFoundException, SQLException {

        Statement statement = con.createStatement();

        statement.executeUpdate("INSERT INTO " + tankMeasuresName + " (timestamp, "
                + "tank_id, fuel_height, fuel_volume, temperature) VALUES ('"
                + Timestamp.valueOf(singleRow.get(0)) + "', "
                + Integer.parseInt(singleRow.get(3)) + ", "
                + Double.parseDouble(singleRow.get(4).replace(",", ".")) + ", "
                + Double.parseDouble(singleRow.get(5).replace(",", ".")) + ", "
                + Integer.parseInt(singleRow.get(6))
                + " )");
        //System.out.println("Data inserted");
    }

    /**
     * Method adds data from refuel.log to database
     * @param singleRow - list with data from refuel.log - single row
     * @param refuelName - string with refuel table name
     * @param con - connection object
     * @throws ClassCastException
     * @throws SQLException 
     */
    private void saveToRefuel(List<String> singleRow, String refuelName,
            Connection con) throws ClassCastException, SQLException {

        Statement statement = con.createStatement();

        statement.executeUpdate("INSERT INTO " + refuelName + " (timestamp, tank_id, "
                + "fuel_volume, tank_speed) VALUES ('"
                + Timestamp.valueOf(singleRow.get(0)) + "', "
                + Integer.parseInt(singleRow.get(1)) + ", "
                + Double.parseDouble(singleRow.get(2).replace(",", ".")) + ", "
                + Double.parseDouble(singleRow.get(3).replace(",", "."))
                + ")");
        //System.out.println("Data inserted");
    }

    /**
     * Method loading file with data
     * @param path - path to file with data
     * @return bufferedReader object
     */
    private BufferedReader loadFileWithData(String path) {

        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(path));
            return bufferedReader;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Method prepare single row with data to database
     * @param line - line with data form data file
     * @return list with separated data
     * @throws IOException 
     */
    private List<String> prepareSingleRow(String line) throws IOException {

        StringBuilder singleString = new StringBuilder();

        List<String> singleRow = new LinkedList();

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) != ';') {
                singleString.append(line.charAt(i));
            } else {
                singleRow.add(singleString.toString());
                singleString = new StringBuilder();
            }
        }
        singleRow.add(singleString.toString());

        return singleRow;
    }

    /***************************************************************************
     ************************** DATA PROCESSING ********************************
     ***************************************************************************/
    
    /**
     * Method prepares .xls file for output data
     * @param outputDataFileName - string with file name
     */
    private void prepareExcelFileForData(String outputDataFileName) {

        HSSFWorkbook workbook = new HSSFWorkbook();
        HSSFSheet sheet = null;

        for (int i = 1; i < 5; i++) {
            sheet = workbook.createSheet("P_tankID_" + i);
        }
        
        for (int i = 1; i < 5; i++) {
            sheet = workbook.createSheet("Z_tankID_" + i);
        }

        try {
            FileOutputStream outputStream = new FileOutputStream(outputDataFileName);
            workbook.write(outputStream);

            workbook.close();
            outputStream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method prepares, sends queries to database and initiates the downloading 
     * of data from tankMeasures tables
     * @param dataType - type of data - primary or distorted
     * @param tankID - id of tank
     * @param setNumber - number of data set
     * @param timeStampList - list for timestamps
     * @param heightList - list for heights
     * @param volumeList - list for volumes
     */
    private void selectDataFromTankMeasures(String dataType, Integer tankID, Integer setNumber,
            List<Timestamp> timeStampList, List<Double> heightList, List<Double> volumeList) {

        Connection con = null;

        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527/hd_project", "root", "root");

            Statement statement = con.createStatement();

            ResultSet rs = statement.executeQuery("SELECT * "
                    + "FROM "
                    + dataType
                    + "_ZESTAW_"
                    + setNumber.toString()
                    + "_TANK_MEASURES WHERE TANK_ID = "
                    + tankID.toString());

            loadTimeStampHeightVolume(rs, timeStampList, heightList, volumeList);

            rs.close();
        } catch (SQLException sqle) {
            System.err.println("SQL exception: " + sqle.getMessage());
        } catch (ClassNotFoundException cnfe) {
            System.err.println("ClassNotFound exception: " + cnfe.getMessage());
        } finally {
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method prepares, sends queries to database and initiates the downloading 
     * of data from nozzleMeasures tables
     * @param dataType - type of data - primary or distorted
     * @param tankID - id of tank
     * @param nozzleID - id of nozzle
     * @param setNumber - number of data set
     * @param totalCounterList - list for total counter volume
     */
    private void selectDataFromNozzleMeasures(String dataType, Integer tankID, 
            Integer nozzleID, Integer setNumber, List<Double> totalCounterList) {

        Connection con = null;

        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527/hd_project", "root", "root");

            Statement statement = con.createStatement();
            
            ResultSet rs = statement.executeQuery("SELECT * "
                    + "FROM "
                    + dataType
                    + "_ZESTAW_"
                    + setNumber.toString()
                    + "_NOZZLE_MEASURES WHERE TANK_ID = "
                    + tankID.toString()
                    + " AND NOZZLE_ID = "
                    + nozzleID.toString());

            loadTotalCounter(rs, totalCounterList);

            filtrDataFromNozzle(totalCounterList);

            rs.close();
        } catch (SQLException sqle) {
            System.err.println("SQL exception: " + sqle.getMessage());
        } catch (ClassNotFoundException cnfe) {
            System.err.println("ClassNotFound exception: " + cnfe.getMessage());
        } finally {
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method prepares, sends queries to database and initiates the downloading 
     * of data from refuel tables
     * @param dataType - type of data - primary or distorted
     * @param tankID - id of tank
     * @param setNumber - number of data set
     * @param timeStampList - list for timestamps
     * @param volumeList - list for volumes
     */
    private void selectDataFromRefuel(String dataType, Integer tankID, Integer setNumber,
            List<Timestamp> timeStampList, List<Double> volumeList) {

        Connection con = null;

        try {
            Class.forName("org.apache.derby.jdbc.ClientDriver");

            con = DriverManager.getConnection("jdbc:derby://localhost:1527/hd_project", "root", "root");

            Statement statement = con.createStatement();

            ResultSet rs = statement.executeQuery("SELECT * "
                    + "FROM "
                    + dataType
                    + "_ZESTAW_"
                    + setNumber.toString()
                    + "_REFUEL WHERE TANK_ID = "
                    + tankID.toString());

            loadRefuelData(rs, timeStampList, volumeList);

            rs.close();
        } catch (SQLException sqle) {
            System.err.println("SQL exception: " + sqle.getMessage());
        } catch (ClassNotFoundException cnfe) {
            System.err.println("ClassNotFound exception: " + cnfe.getMessage());
        } finally {
            try {
                if (con != null) {
                    con.close();
                }
            } catch (SQLException sqle) {
                System.err.println("SQL exception: " + sqle.getMessage());
            }
        }
    }

    /**
     * Method gets data from ResultSet (tankMeasures tables) and adds them to 
     * the individual lists
     * @param rs - Resultset object
     * @param timestampList - list for timestamps
     * @param heightList - list for heights
     * @param volumeList - list for volumes
     * @throws SQLException 
     */
    private void loadTimeStampHeightVolume(ResultSet rs, List<Timestamp> timestampList,
            List<Double> heightList, List<Double> volumeList) throws SQLException {

        Integer counter = 0;
        
        if(!timestampList.isEmpty()){
            timestampList.clear();
        }
        
        while (rs.next()) {

            if (counter % 12 == 0) {
                timestampList.add(rs.getTimestamp("timestamp"));
                heightList.add(rs.getDouble("fuel_height"));
                volumeList.add(rs.getDouble("fuel_volume"));
            }
            counter++;
        }
    }

    /**
     * Method gets data from ResultSet (refuel tables) and adds them to the list
     * with volume of total counter
     * @param rs - Resultset object
     * @param totalCounterList - list for total counter volume
     * @throws SQLException 
     */
    private void loadTotalCounter(ResultSet rs, List<Double> totalCounterList)
            throws SQLException {

        while (rs.next()) {
            totalCounterList.add(rs.getDouble("total_counter"));
        }
    }

    /**
     * Method gets data from ResultSet (refuel tables) and adds them to the
     * individual lists
     * @param rs - Resultset object
     * @param timeStampList - list for timestamps
     * @param volumeList - list for volumes
     * @throws SQLException 
     */
    private void loadRefuelData(ResultSet rs, List<Timestamp> timeStampList,
            List<Double> volumeList) throws SQLException {

        while (rs.next()) {
            timeStampList.add(rs.getTimestamp("timestamp"));
            volumeList.add(rs.getDouble("fuel_volume"));
        }
    }

    /**
     * Method prepares volume list (hourly time intervals) based on timestamp lists
     * for sets number one and two
     * @param timeStampList - timestamp list from tankMeasures
     * @param timeStampRefuelList - timestamp list from refuel
     * @param volumeRefuelList - volume list from refuel
     * @return volume list with hourly time intervals
     */
    private List<Double> addFuelFromRefuelOneTwo(List<Timestamp> timeStampList,
            List<Timestamp> timeStampRefuelList, List<Double> volumeRefuelList) {

        List<Double> addedFuel = new LinkedList<>();

        for (int i = 0; i < timeStampList.size(); i++) {
            addedFuel.add(0.0);
        }

        Integer minutes = 0;
        Double volume = 0.0;

        for (int i = 0; i < timeStampRefuelList.size(); i++) {

            for (int j = 0; j < timeStampList.size() - 1; j++) {

                if ((timeStampRefuelList.get(i)).before(timeStampList.get(j + 1))
                        && (timeStampRefuelList.get(i)).after(timeStampList.get(j))) {

                    minutes = Math.toIntExact(timeStampList.get(j + 1).getTime()
                            - timeStampRefuelList.get(i).getTime());

                    minutes /= 60000;

                    volume = minutes * 333.333333333333;

                    if (volume > volumeRefuelList.get(i)) {
                        addedFuel.set(j, volumeRefuelList.get(i));
                    } else {
                        addedFuel.set(j, volume);
                        addedFuel.set(j + 1, volumeRefuelList.get(i) - volume);
                    }

                }
            }
        }
        return addedFuel;
    }

    /**
     * Method prepares volume list (hourly time intervals) based on timestamp lists
     * for set three
     * @param timeStampList - timestamp list from tankMeasures
     * @param timeStampRefuelList - timestamp list from refuel
     * @param volumeRefuelList - volume list from refuel
     * @return volume list with hourly time intervals
     */
    private List<Double> addFuelFromRefuelThree(List<Timestamp> timeStampList,
            List<Timestamp> timeStampRefuelList, List<Double> volumeRefuelList) {

        List<Double> addedFuel = new LinkedList<>();

        for (int i = 0; i < timeStampList.size(); i++) {
            addedFuel.add(0.0);
        }

        Map<Integer, Double> tempMap = new HashMap<>();

        for (int i = 0; i < timeStampRefuelList.size(); i++) {

            for (int j = 0; j < timeStampList.size() - 1; j++) {

                if ((timeStampRefuelList.get(i)).before(timeStampList.get(j + 1))
                        && (timeStampRefuelList.get(i)).after(timeStampList.get(j))) {

                    if (volumeRefuelList.get(i) > addedFuel.get(j)) {
                        addedFuel.set(j, volumeRefuelList.get(i));
                    } else {
                        addedFuel.set(j, addedFuel.get(j) + 333.333333333333);

                        if (tempMap.containsKey(j + 1)) {
                            tempMap.replace(j + 1, tempMap.get(j + 1) + 333.333333333333);
                        } else {
                            tempMap.put(j + 1, 333.333333333333);
                        }
                    }
                }
            }
        }

        for (int i = 1; i < addedFuel.size(); i++) {

            if (addedFuel.get(i) > addedFuel.get(i - 1)) {
                addedFuel.set(i, addedFuel.get(i) - addedFuel.get(i - 1));
            } else if (tempMap.containsKey(i) && addedFuel.get(i) > tempMap.get(i)) {
                addedFuel.set(i, addedFuel.get(i) - tempMap.get(i) + 333.333333333333);
            }
        }

        return addedFuel;
    }

    /**
     * Method moves elements from list for one item
     * @param list - list with elements to move
     */
    private void moveForAnHour(List<Double> list) {

        list.add(0, 0.0);
        list.remove(list.size() - 1);
    }

    /**
     * Method selects hourly values
     * @param totalCounterList - list with totalCounter values
     */
    private void filtrDataFromNozzle(List<Double> totalCounterList) {

        List<Double> tempTotalCList = new LinkedList<>();

        for (int i = 0; i < totalCounterList.size(); i++) {

            if (i % 60 == 0) {
                tempTotalCList.add(totalCounterList.get(i));
            }
        }
        totalCounterList.clear();

        for (int i = 0; i < tempTotalCList.size(); i++) {
            totalCounterList.add(tempTotalCList.get(i));
        }
    }

    /**
     * Method sum individual items of lists
     * @param listOne - first list with data
     * @param listTwo - second list with data
     * @param listThree - third list with data
     * @return list with sum of items
     */
    private List<Double> sumList(List<Double> listOne, List<Double> listTwo, List<Double> listThree) {

        List<Double> resultList = new LinkedList<>();

        for (int i = 0; i < listOne.size(); i++) {
            resultList.add(listOne.get(i) + listTwo.get(i) + listThree.get(i));
        }
        return resultList;
    }

    /**
     * Method calculates hourly volumes based on volume pumped out by nozzles
     * @param volumeTankMeasures - volume list from tankMeasures
     * @param nozzlePumpedOutList - volume list with fuel pumped out
     * @param refuelList - volume lisst from refuel
     * @return list with calculated hourly volume
     */
    private List<Double> calculateVolume(List<Double> volumeTankMeasures, List<Double> nozzlePumpedOutList,
            List<Double> refuelList) {

        List<Double> resultVolumeList = new LinkedList<>();

        Double pumpedHourBefore = 0.0;
        Double pumpedHourNow = 0.0;
        Double subPumped = 0.0;

        Double volumeTankHourBefore = volumeTankMeasures.get(0);

        for (int i = 0; i < nozzlePumpedOutList.size(); i++) {

            if (i > 0) {
                pumpedHourBefore = nozzlePumpedOutList.get(i - 1);
                pumpedHourNow = nozzlePumpedOutList.get(i);
                volumeTankHourBefore = volumeTankMeasures.get(i - 1);
            }
            subPumped = pumpedHourNow - pumpedHourBefore;

            resultVolumeList.add(volumeTankHourBefore - subPumped + refuelList.get(i));
        }
        return resultVolumeList;
    }

    /**
     * Method updates average list based on list with new data
     * @param averageList - list with updating average
     * @param newDataList - list with new data
     */
    private void addToAverageList(List<Double> averageList, List<Double> newDataList) {

        if (averageList.size() == 0) {

            for (int i = 0; i < newDataList.size(); i++) {
                averageList.add(newDataList.get(i));
            }
        } else {
            for (int i = 0; i < newDataList.size(); i++) {
                averageList.set(i, (averageList.get(i) + newDataList.get(i)) / 2);
            }
        }
    }

    /**
     * Method saves output data to excel file
     * @param dataType - type of data - primary or distorted
     * @param tankID - id of tank
     * @param heightTankList - list with height from tankMeasures
     * @param volumeTankList - list with voluem from tankMeasures
     * @param volumeFromNozzleList - list with calculated volume based on nozzles
     */
    private void saveDataToExcelFile(String dataType, Integer tankID, List<Double> heightTankList,
            List<Double> volumeTankList, List<Double> volumeFromNozzleList) {

        String fileName = "outputData.xls";
        HSSFWorkbook workbook = null;

        try {
            FileInputStream fileInputStream = new FileInputStream(fileName);

            workbook = new HSSFWorkbook(fileInputStream);
            
            HSSFSheet sheet = null;

            if(dataType.equals("PIERWOTNE")){
                sheet = workbook.getSheet("P_tankID_" + tankID.toString());
            }else{
                sheet = workbook.getSheet("Z_tankID_" + tankID.toString());
            }
            
            Row row = sheet.createRow(0);

            Cell heightCell = row.createCell(0);
            heightCell.setCellStyle(setCellStyle(workbook));

            Cell volumeTankCell = row.createCell(1);
            volumeTankCell.setCellStyle(setCellStyle(workbook));

            Cell volumeNozzleCell = row.createCell(2);
            volumeNozzleCell.setCellStyle(setCellStyle(workbook));

            Cell aplusbxCell = row.createCell(5);
            aplusbxCell.setCellStyle(setCellStyle(workbook));

            heightCell.setCellValue("Height");
            volumeTankCell.setCellValue("V - Tank");
            volumeNozzleCell.setCellValue("V - Nozzle");
            aplusbxCell.setCellValue("y=a+bx");

            Double currentHeight = heightTankList.get(0);

            Integer rowCounter = 0;
            Integer counterForAPlusBX = 0;

            for (int i = 0; i < heightTankList.size(); i++) {

                currentHeight = heightTankList.get(i);

                rowCounter++;

                row = sheet.createRow(rowCounter);

                heightCell = row.createCell(0);
                volumeTankCell = row.createCell(1);
                volumeNozzleCell = row.createCell(2);
                aplusbxCell = row.createCell(5);
                //stdDevCell = row.createCell(2);
                //CVCell = row.createCell(3);
                //aplusbxCell = row.createCell(7);
                //nozzeleVolumeCell = row.createCell(8);

                if (rowCounter == 2) {
                    Cell aCell = row.createCell(3);
                    aCell.setCellStyle(setCellStyle(workbook));
                    aCell.setCellValue("a =");
                    aCell = row.createCell(4);
                    aCell.setCellStyle(setCellStyleDecimal(workbook));
                    aCell.setCellFormula("INTERCEPT(C2:C169,A2:A169)");
                }

                if (rowCounter == 3) {
                    Cell bCell = row.createCell(3);
                    bCell.setCellStyle(setCellStyle(workbook));
                    bCell.setCellValue("b =");
                    bCell = row.createCell(4);
                    bCell.setCellStyle(setCellStyleDecimal(workbook));
                    bCell.setCellFormula("SLOPE(C2:C169,A2:A169)");
                }

                heightCell.setCellValue(heightTankList.get(i).intValue());
                volumeTankCell.setCellValue(volumeTankList.get(i).intValue());
                volumeNozzleCell.setCellValue(volumeFromNozzleList.get(i).intValue());

                //stdDevCell.setCellStyle(setCellStyleDecimal(workbook));
                //stdDevCell.setCellValue(stdDeviationList.get(setNO).intValue());
                //CVCell.setCellStyle(setCellStyleDecimal(workbook));
                //CVCell.setCellValue(CVlist.get(setNO));
                counterForAPlusBX = rowCounter + 1;

                aplusbxCell.setCellStyle(setCellStyleDecimal(workbook));
                aplusbxCell.setCellFormula("SUM(E3,PRODUCT(E4,A" + counterForAPlusBX + "))");

                //nozzeleVolumeCell.setCellValue(volumeFromNozzleList.get(setNO).intValue());
            }
            //sheet.setColumnWidth(7, 3000);

            fileInputStream.close();

            FileOutputStream outputStream = new FileOutputStream(fileName);
            workbook.write(outputStream);

            workbook.close();
            outputStream.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method set cell style - bold font, central alignment 
     * @param workbook - HSSFWorkbook object
     * @return cell style
     */
    private CellStyle setCellStyle(HSSFWorkbook workbook) {

        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);

        HSSFFont font = workbook.createFont();
        font.setBold(true);

        style.setFont(font);

        return style;
    }

    /**
     * Method set cell style - decimal data format
     * @param workbook - HSSFWorkbook object
     * @return cell style
     */
    private CellStyle setCellStyleDecimal(HSSFWorkbook workbook) {

        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("0"));

        return style;
    }
}