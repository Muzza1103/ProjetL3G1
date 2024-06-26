package com.l3g1.apitraveller.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.l3g1.apitraveller.cache.CacheConfig;
import com.l3g1.apitraveller.model.*;
import com.l3g1.apitraveller.model.enumeration.*;
import com.l3g1.apitraveller.repository.CityRepository;
import com.l3g1.apitraveller.repository.CountryRepository;
import com.l3g1.apitraveller.repository.ActivityRepository;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import lombok.Data;
import java.time.LocalDate;

@Data
@Service

public class TripService {

    @Autowired
    private CountryRepository countryRepository;


    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private AiService aiService;

    @Autowired
    private CountryService countryService;

    @Autowired
    private CityService cityService;

    @Autowired
    private CacheConfig cacheConfig;

    // Generates suggestions based on the survey criteria provided with the data from the database
    // If the corresponding data is not present in the database, it returns nothing.
    public List<TripSuggestion> getSuggestion(TripSurvey survey) throws IllegalArgumentException{

        // Extract survey parameters
        if(Continent.isValidValue(survey.getLocalisation().toUpperCase().replace(" ", "_"))){
            survey.setLocalisation(survey.getLocalisation().toUpperCase().replace(" ", "_"));
        }else if (!survey.getLocalisation().equals("ALL")) {
            String localisation = survey.getLocalisation().toLowerCase().replace("_", " ");
            // Capitalizes the first letter of the location
            localisation = Character.toUpperCase(localisation.charAt(0)) + localisation.substring(1);
            // Capitalizes the first letter after each space in the location
            for (int i = 0; i < localisation.length(); i++) {
                if (localisation.charAt(i) == ' ') {
                    localisation = localisation.substring(0, i + 1) + Character.toUpperCase(localisation.charAt(i + 1)) + localisation.substring(i + 2);
                }
            }
            survey.setLocalisation(localisation);
        }
        String localisation = survey.getLocalisation();
        Climate climate = survey.getClimate();
        Boolean roadTrip = survey.isRoadTrip();
        Landscape landscape = survey.getLandscape();
        Temperature temperature = survey.getTemperature();
        List<ActivityType> activityType = survey.getActivityType();
        // Parsing departure and arrival dates into LocalDate
        LocalDate startingDate = LocalDate.parse(survey.getStartingDate());
        LocalDate endingDate = LocalDate.parse(survey.getEndingDate());
        int budget = survey.getBudget();

        // Initializing variables
        Country countryDest = null;
        TripSuggestion suggestionFinal;
        List<Country> countryList = new ArrayList<>();
        List<City> cityList;
        Random random;
        int randomIndex;
        int numberOfDaysPerCity;
        City cityDest = null;
        List<Activity> activityDest = null;
        int cost = 0;
        StringBuilder dayActivityBuilder = new StringBuilder();
        List<TripSuggestion> listTripSuggestion = new ArrayList<>();
        random = new Random();

        // Generate suggestions based on survey criteria
        if (localisation.equals("ALL")||Continent.isValidValue(localisation.toUpperCase().replace(" ", ""))|| RecognizedCountry.isValidValue(localisation.toUpperCase().replace(" ", ""))){
            // Manage the code for both cases as the code is almost the same for the two, only the initialisation of countryList change
            if (localisation.equals("ALL")) { // No specific country specified by the user, the function will return 1 to 3 suggestions of different countries
                // Retrieve list of countries based on the Climate in the survey
                if (climate.equals(Climate.ALL)) {
                    countryList = countryRepository.findAll();
                } else {
                    countryList = countryService.findAllByClimateListContaining(climate);
                }
                if(!countryList.isEmpty()) {
                    // Select a random country in the list of country
                    randomIndex = random.nextInt(countryList.size());
                    countryDest = countryList.get(randomIndex);
                }
            }else if(Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) { // A specific continent is given by the user, the function will return 1 to 3 suggestions of different countries in the specified continent
                // Retrieve the specified country based on the given continent and climate

                // Handle an error where the AI return erroneous values
                if(survey.getClimate() == Climate.MEDITERRANEAN && !survey.getLocalisation().equals(Continent.EUROPE.toString()) && !survey.getLocalisation().equals(Continent.AFRICA.toString())){
                    survey.setClimate(Climate.TEMPERATE);
                    climate = Climate.TEMPERATE;
                }

                if (climate.equals(Climate.ALL)) {
                    countryList = countryRepository.findAllByContinent(Continent.valueOf(localisation.toUpperCase()));
                } else {
                    countryList = countryService.findCountryByContinentAndClimateListContaining(Continent.valueOf(localisation.toUpperCase()), climate);
                }
                if(!countryList.isEmpty()) {
                    // Select a random country in the list of country
                    randomIndex = random.nextInt(countryList.size());
                    countryDest = countryList.get(randomIndex);
                }
            }else if(RecognizedCountry.isValidValue(localisation.toUpperCase().replace(" ", "_"))){
                if(climate.equals(Climate.ALL)){
                    countryDest = countryRepository.findByCountryName(localisation);
                }else{
                    countryDest = countryService.findCountryByCountryNameAndClimateListContaining(localisation, climate);
                }
            }

            if(countryDest!=null){
                // Filter cities in the selected country based on criteria
                cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);

                // Verify if the user want a suggestion for a travel through multiple cities or not
                if (!roadTrip) {
                    if (!cityList.isEmpty()) {
                        // Select a random city in the list of city
                        int randomIndex2 = random.nextInt(cityList.size());
                        cityDest = cityList.get(randomIndex2);
                        activityDest = new ArrayList<>();
                        // Retrieve activities in the selected city based on activity types

                        for (ActivityType activityType1 : activityType) {
                            activityDest.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                        }

                    }
                    if (activityDest!=null && !activityDest.isEmpty()) {
                        List<Activity> activities = new ArrayList<>();
                        List<Activity> chosenActivities = new ArrayList<>();
                        long timeTrip = ChronoUnit.DAYS.between(startingDate, endingDate); // Convert the trip's date into days
                        int activitiesPerDay = 2; // Number of activities per day

                        // A loop that assigns one activity per day based on the duration of the trip.
                        for(Activity act : activityDest){
                            if (act.getPrice()<=budget){
                                activities.add(act);
                            }
                        }

                        //travels through each day of the trip, select available activities based on the budget
                        //the details of each selected activity are added
                        for (int day = 0; day < timeTrip; day++) {
                            dayActivityBuilder.append("\n Day ").append(day + 1).append(" : \n\n");
                            for (int activityIndex = 0; activityIndex < activitiesPerDay; activityIndex++) {
                                Activity dailyActivity = null;
                                if(!activities.isEmpty()) {
                                    int costValue = budget - cost;
                                    for(Activity activity : activities){
                                        if ((activity.getPrice()<costValue)&&!chosenActivities.contains((activity))){
                                            dailyActivity = activity;
                                            costValue = activity.getPrice();
                                        }
                                    }
                                    if(dailyActivity!=null) {
                                        cost += dailyActivity.getPrice();
                                        chosenActivities.add(dailyActivity);
                                    }
                                }
                                if (dailyActivity!=null) {
                                    dayActivityBuilder.append("  Activity : ").append(dailyActivity.getActivityName()).append("\n");
                                    dayActivityBuilder.append("    Type of the activity: ").append(dailyActivity.getActivityType()).append("\n");
                                    dayActivityBuilder.append("    Description: ").append(dailyActivity.getDescription()).append("\n");
                                    dayActivityBuilder.append("    Price: ").append(dailyActivity.getPrice()).append("\n\n");

                                } else {
                                    dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                }
                            }
                        }
                        String dayActivity = dayActivityBuilder.toString();
                        suggestionFinal = new TripSuggestion(countryDest, cityDest, chosenActivities, survey, startingDate, endingDate, dayActivity,cost);
                        listTripSuggestion.add(suggestionFinal);
                    }
                }
                else {//generates the activities for each day of the trip when the trip is a road trip.
                    //randomly selects a city to visit, retrieves the activities available in this city, and distributes them over the days of the trip according to the remaining budget
                    int day = 0;
                    long timeTrip = ChronoUnit.DAYS.between(startingDate, endingDate); // Convert the trip's date into days
                    numberOfDaysPerCity = (int)Math.ceil((double)timeTrip/ cityList.size()); // Calculate the number of days per city

                    while (day<timeTrip) {
                        int costCity =0;
                        String dayActivity = "";
                        dayActivityBuilder = new StringBuilder();
                        if (!cityList.isEmpty()) {
                            // Select a random city in the list of city
                            int randomIndex2 = random.nextInt(cityList.size());
                            cityDest = cityList.get(randomIndex2);
                            activityDest = new ArrayList<>();

                            // Retrieve activities in the selected city based on activity types
                            for (ActivityType activityType1 : activityType) {
                                activityDest.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                            }

                            // Check if activities are available in the selected city
                            if (!activityDest.isEmpty()) {
                                List<Activity> activities = new ArrayList<>();
                                List<Activity> chosenActivities = new ArrayList<>();
                                int activitiesPerDay = 2; // Number of activities per day
                                for(Activity act : activityDest){
                                    if (act.getPrice()<=budget){
                                        activities.add(act);
                                    }
                                }

                                // Assign activities to each day based on the duration of the trip
                                for (int j = 0; j<numberOfDaysPerCity; j++) {
                                    dayActivityBuilder.append("\n Day-").append(day + 1 ).append(" : \n\n");

                                    // Loop through each activity per day
                                    for (int activityIndex = 0; activityIndex < activitiesPerDay; activityIndex++) {
                                        Activity dailyActivity = null;
                                        if(!activities.isEmpty()) {
                                            int costValue = budget - cost;

                                            // Randomly select an activity that fits the budget
                                            while (!activities.isEmpty() && dailyActivity==null) {
                                                int randomIdx = random.nextInt(activities.size());
                                                Activity activity = activities.get(randomIdx);

                                                // Check if the activity fits the budget and hasn't been chosen before
                                                if (activity.getPrice() <= costValue && !chosenActivities.contains(activity)) {
                                                    dailyActivity = activity;
                                                }
                                                activities.remove(randomIdx);
                                            }

                                            // If an activity is selected, update cost and add it to chosenActivities
                                            if(dailyActivity!=null) {
                                                cost += dailyActivity.getPrice();
                                                costCity += dailyActivity.getPrice();
                                                chosenActivities.add(dailyActivity);
                                            }
                                        }
                                        // Append activity details to the dayActivityBuilder
                                        if (dailyActivity!=null) {
                                            dayActivityBuilder.append("  Activity : ").append(dailyActivity.getActivityName()).append("\n");
                                            dayActivityBuilder.append("    Type of the activity: ").append(dailyActivity.getActivityType()).append("\n");
                                            dayActivityBuilder.append("    Description: ").append(dailyActivity.getDescription()).append("\n");
                                            dayActivityBuilder.append("    Price: ").append(dailyActivity.getPrice()).append("\n\n");
                                        } else {
                                            dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                        }
                                    }
                                    day++;
                                    if(day>=timeTrip){
                                        break;
                                    }
                                }
                                dayActivity = dayActivityBuilder.toString();
                                suggestionFinal = new TripSuggestion(countryDest, cityDest, chosenActivities, survey, startingDate, endingDate, dayActivity, cost, costCity);
                                listTripSuggestion.add(suggestionFinal); // Add the suggestion to the list of trip suggestions
                            }else{
                                dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                day++;
                            }

                            // Remove the selected city from the list to avoid duplicates
                            cityList.remove(randomIndex2);
                        }else{
                            break; // Break the loop if the list of cities is empty
                        }
                    }
                }
            }
        }else{
            throw new IllegalArgumentException("The localisation you have given in the survey is incorrect, please check the survey.");
        }
        // Return the list of suggestions
        return listTripSuggestion;
    }

    // Generates suggestions based on the survey criteria provided with the data from the database
    // Call the AI api to return a list of suggestions
    @SneakyThrows
    public List<TripSuggestion> getSuggestionAI(TripSurvey survey) throws IllegalArgumentException{

        // Extract survey parameters
        // Correct the localisation given by the user to avoid creating errors in the database related to typing issues
        if(Continent.isValidValue(survey.getLocalisation().toUpperCase().replace(" ", "_"))){
            survey.setLocalisation(survey.getLocalisation().toUpperCase().replace(" ", "_"));
        }else if (!survey.getLocalisation().equals("ALL")) {
            String localisation = survey.getLocalisation().toLowerCase().replace("_", " ");
            // Capitalizes the first letter of the location
            localisation = Character.toUpperCase(localisation.charAt(0)) + localisation.substring(1);
            // Capitalizes the first letter after each space in the location
            for (int i = 0; i < localisation.length(); i++) {
                if (localisation.charAt(i) == ' ') {
                    localisation = localisation.substring(0, i + 1) + Character.toUpperCase(localisation.charAt(i + 1)) + localisation.substring(i + 2);
                }
            }
            survey.setLocalisation(localisation);
        }
        String localisation = survey.getLocalisation();
        survey.setLocalisation(localisation);
        Climate climate = survey.getClimate();
        Boolean roadTrip = survey.isRoadTrip();
        Landscape landscape = survey.getLandscape();
        Temperature temperature = survey.getTemperature();
        List<ActivityType> activityType = survey.getActivityType();
        // Parsing departure and arrival dates into LocalDate
        LocalDate startingDate = LocalDate.parse(survey.getStartingDate());
        LocalDate endingDate = LocalDate.parse(survey.getEndingDate());
        int budget = survey.getBudget();

        // Initializing variables
        Country countryDest = null;
        TripSuggestion suggestionFinal;
        List<Country> countryList = new ArrayList<>();
        List<City> cityList;
        Random random;
        int randomIndex;
        int numberOfDaysPerCity;
        City cityDest;
        List<Activity> activityDest;
        int cost = 0;
        StringBuilder dayActivityBuilder = new StringBuilder();
        List<TripSuggestion> listTripSuggestion = new ArrayList<>();
        random = new Random();
        String jsonString;
        ObjectMapper objectMapper;
        int iterationCountryAI=0;
        int iterationCityAI=0;
        int iterationActivityAI=0;
        int iterationDailyActivityAI=0;

        List<TripSuggestion> randomSuggestionFromCache;

        List<List<TripSuggestion>> suggestionsFromCache = cacheConfig.getTripSuggestionFromCache(survey);
        if (suggestionsFromCache!=null&&!suggestionsFromCache.isEmpty()) {
            randomSuggestionFromCache = suggestionsFromCache.get(random.nextInt(suggestionsFromCache.size()));
            return randomSuggestionFromCache;
        }

        // Gets the value of the request per minute of the api key from an environment variable which is defined by the user prior to the launch
        int intValue = 3500;
        try {
            String maVariableEnv = System.getenv("RPM_API_KEY");
            intValue = Integer.parseInt(maVariableEnv);
        } catch (NumberFormatException e) {
            System.out.println("The environment variable RPM_API_KEY must be defined and must be an integer.");
        }

        //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
        int numberOfRequestPerMinute = intValue;
        long minMillisecondBetweenRequest = (60/numberOfRequestPerMinute)*1000;
        Instant timeLastRequest = null;


        // Generate suggestions based on survey criteria
        if (localisation.equals("ALL")||Continent.isValidValue(localisation.toUpperCase().replace(" ", ""))||RecognizedCountry.isValidValue(localisation.toUpperCase().replace(" ", "_"))){
            // Manage the code for both cases as the code is almost the same for the two, only the initialisation of countryList change
            if (localisation.equals("ALL")||Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) {
                // No specific country specified by the user, the function will return 1 to 3 suggestions of different countries
                if(localisation.equals("ALL")){
                    // Retrieve list of countries based on the Climate in the survey
                    if (climate.equals(Climate.ALL)) {
                        countryList = countryRepository.findAll();
                    } else {
                        countryList = countryService.findAllByClimateListContaining(climate);
                    }
                }else if(Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) {
                    // A specific continent is given by the user, the function will return 1 to 3 suggestions of different countries in the specified continent
                    // Handle an error where the AI return erroneous values
                    if(survey.getClimate() == Climate.MEDITERRANEAN && !survey.getLocalisation().equals(Continent.EUROPE.toString()) && !survey.getLocalisation().equals(Continent.AFRICA.toString())){
                        survey.setClimate(Climate.TEMPERATE);
                        climate = Climate.TEMPERATE;
                        suggestionsFromCache = cacheConfig.getTripSuggestionFromCache(survey);
                        if (suggestionsFromCache!=null&&!suggestionsFromCache.isEmpty()) {
                            randomSuggestionFromCache = suggestionsFromCache.get(random.nextInt(suggestionsFromCache.size()));
                            return randomSuggestionFromCache;
                        }
                    }
                    // Retrieve the specified country based on the given continent and climate
                    if (climate.equals(Climate.ALL)) {
                        countryList = countryRepository.findAllByContinent(Continent.valueOf(localisation.toUpperCase()));
                    } else {
                        countryList = countryService.findCountryByContinentAndClimateListContaining(Continent.valueOf(localisation.toUpperCase()), climate);
                    }
                }
                if(!countryList.isEmpty()) {
                    // Select a random country in the list of country
                    randomIndex = random.nextInt(countryList.size());
                    countryDest = countryList.get(randomIndex);
                }else{
                    // Add suggested countries to the list and retry if the list is empty
                    while (countryList.size() < 3 && iterationCountryAI<10) {
                        try {
                            objectMapper = new ObjectMapper();
                            jsonString = aiService.chatTripCountry(survey);
                            Country country = objectMapper.readValue(jsonString, Country.class);
                            // Add the new country to the database
                            countryService.addCountry(country);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        // Retrieve updated country list
                        if (localisation.equals("ALL")) {
                            if (climate.equals(Climate.ALL)) {
                                countryList = countryRepository.findAll();
                            } else {
                                countryList = countryService.findAllByClimateListContaining(climate);
                            }
                        } else if (Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) {
                            if (climate.equals(Climate.ALL)) {
                                countryList = countryRepository.findAllByContinent(Continent.valueOf(localisation.toUpperCase().replace(" ", "_")));

                            } else {
                                countryList = countryService.findCountryByContinentAndClimateListContaining(Continent.valueOf(localisation.toUpperCase().replace(" ", "_")), climate);
                            }
                        }
                        iterationCountryAI++;
                    }
                    if(!countryList.isEmpty()) {
                        // Select a random country from the updated list
                        randomIndex = random.nextInt(countryList.size());
                        countryDest = countryList.get(randomIndex);
                    }
                }
            }else if(RecognizedCountry.isValidValue(localisation.toUpperCase().replace(" ", "_"))){
                // Retrieve the specified country based on the given name and climate
                if(climate.equals(Climate.ALL)){
                    countryDest = countryRepository.findByCountryName(localisation);
                }else{
                    countryDest = countryService.findCountryByCountryNameAndClimateListContaining(localisation, climate);
                }
                // Retry until the specified country is found in the database
                while(countryDest==null && iterationCountryAI<3) {
                    // Call the AI to add the new country based on the information on the survey
                    try{
                        objectMapper = new ObjectMapper();
                        jsonString = aiService.chatTripCountry(survey);
                        Country country = objectMapper.readValue(jsonString, Country.class);
                        // Handle an error where the AI return erroneous values
                        if(survey.getClimate() == Climate.MEDITERRANEAN && country.getContinent() != Continent.EUROPE && country.getContinent() != Continent.AFRICA){
                            survey.setClimate(Climate.TEMPERATE);
                            climate = Climate.TEMPERATE;
                            suggestionsFromCache = cacheConfig.getTripSuggestionFromCache(survey);
                            if (suggestionsFromCache!=null&&!suggestionsFromCache.isEmpty()) {
                                randomSuggestionFromCache = suggestionsFromCache.get(random.nextInt(suggestionsFromCache.size()));
                                return randomSuggestionFromCache;
                            }
                            throw new IllegalArgumentException("MEDITERRANEAN Climate asked for a continent with no MEDITERRANEAN climate, Correction : The climate is changed from MEDITERRANEAN to TEMPERATE");
                        }
                        // Add suggested country to database
                        countryService.addCountry(country);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    if(climate.equals(Climate.ALL)){
                        countryDest = countryRepository.findByCountryName(localisation);
                    } else {
                        countryDest = countryService.findCountryByCountryNameAndClimateListContaining(localisation, climate);
                    }
                    iterationCountryAI++;
                }

            }

            if(countryDest!=null){
                // Filter cities in the selected country based on criteria
                cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);

                // Verify if the user want a suggestion for a travel through multiple cities or not
                if (!roadTrip) {
                    // Retry until enough cities are found or a maximum iteration count is reached
                    while((cityList == null || cityList.size() < 3 ) && iterationCityAI < 10) {

                        //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                        if (timeLastRequest != null) {
                            long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                            if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                try {
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        timeLastRequest = Instant.now();


                        // Call the AI to add new cities based on the information on the survey
                        try {
                            jsonString = aiService.chatTripCity(countryDest, survey);
                            objectMapper = new ObjectMapper();
                            City city = objectMapper.readValue(jsonString, City.class);
                            // Add suggested city to database
                            cityService.addCity(city);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        // Retrieve updated city list
                        cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);
                        iterationCityAI++;
                    }
                    if(cityList!=null && !cityList.isEmpty()) {
                        // Select a random city in the list of city
                        int randomIndex2 = random.nextInt(cityList.size());
                        cityDest = cityList.get(randomIndex2);
                        activityDest = new ArrayList<>();
                        // Retrieve activities in the selected city based on activity types
                        for (ActivityType activityType1 : activityType) {
                            activityDest.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                        }

                        long timeTrip = ChronoUnit.DAYS.between(startingDate, endingDate); // Convert the trip's date into days
                        int activitiesPerDay = 2; // Number of activities per day

                        // Define the upper and lower limit for the price of the activities
                        Integer lowerLimit = (int) (budget / (timeTrip * activitiesPerDay * 2));
                        Integer upperLimit;
                        int upperLimitFactor = 3; //The higher this factor the more likely it is that the activities are costly, which means that the budget will be reached earlier and that the last activities will be free.
                        if (upperLimitFactor * lowerLimit < budget) {
                            upperLimit = upperLimitFactor * lowerLimit;
                        } else {
                            upperLimit = budget;
                        }

                        if (timeTrip <= 0) {
                            throw new IllegalArgumentException("The dates you have entered are incorrect. Please check them.");
                        }

                        // Retry until enough activities are found or a maximum iteration count is reached
                        while (activityDest.size() < timeTrip * activitiesPerDay && iterationActivityAI < 10) {
                            // Call the AI to add new activities based on the information on the survey

                            //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                            if (timeLastRequest != null) {
                                long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                    long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                    try {
                                        Thread.sleep(waitTime);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            timeLastRequest = Instant.now();

                            try {
                                jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey, upperLimit, lowerLimit);
                                objectMapper = new ObjectMapper();

                                City city = objectMapper.readValue(jsonString, City.class);
                                cityService.addCity(city);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            List<Activity> newActivities = new ArrayList<>();
                            // Retrieve updated activity list for the city
                            for (ActivityType activityType1 : activityType) {
                                newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                            }
                            // Add new activities to suggestion list
                            for (Activity newActivity : newActivities) {
                                // Add the new activity only if it's not already present in activityDest
                                if (!activityDest.contains(newActivity)) {
                                    activityDest.add(newActivity);
                                }
                            }
                            iterationActivityAI++;
                        }
                        //  Extends the activity selection logic for each day of the trip by adding the ability to suggest activities in real time using an artificial intelligence service
                        if (!activityDest.isEmpty()) {
                            List<Activity> chosenActivities = new ArrayList<>();
                            List<Activity> activities;
                            // A loop that assigns one activity per day based on the duration of the trip.
                            for (int day = 0; day < timeTrip; day++) {
                                dayActivityBuilder.append("\n Day-").append(day + 1).append(" : \n\n");
                                for (int activityIndex = 0; activityIndex < activitiesPerDay; activityIndex++) {
                                    Activity dailyActivity = null;
                                    if (!activityDest.isEmpty()) {
                                        int costValue = budget - cost;
                                        activities = new ArrayList<>(activityDest);
                                        // Adjust upper and lower limits based on remaining budget
                                        if (costValue >= (budget) / (timeTrip * activitiesPerDay)) {
                                            lowerLimit = (int) (budget / (timeTrip * activitiesPerDay * 2));
                                            if (upperLimitFactor * lowerLimit < costValue) {
                                                upperLimit = upperLimitFactor * lowerLimit;
                                            } else {
                                                upperLimit = costValue;
                                            }
                                        } else {
                                            lowerLimit = 0;
                                            upperLimit = costValue;
                                        }
                                        // Retry until a suitable daily activity is found
                                        while (!activities.isEmpty() && dailyActivity == null) {
                                            int randomIdx = random.nextInt(activities.size());
                                            Activity activity = activities.get(randomIdx);
                                            if (activity.getPrice() >= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                dailyActivity = activity;
                                            }
                                            activities.remove(randomIdx);
                                        }
                                        iterationDailyActivityAI = 0;
                                        while (dailyActivity == null && iterationDailyActivityAI < 10) {

                                            //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                                            if (timeLastRequest != null) {
                                                long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                                if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                                    long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                                    try {
                                                        Thread.sleep(waitTime);
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                            timeLastRequest = Instant.now();

                                            try {
                                                jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey, upperLimit, lowerLimit);
                                                objectMapper = new ObjectMapper();

                                                City city = objectMapper.readValue(jsonString, City.class);
                                                cityService.addCity(city);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            List<Activity> newActivities = new ArrayList<>();
                                            // Retrieve updated activity list for the city
                                            for (ActivityType activityType1 : activityType) {
                                                newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                                            }
                                            // Add new activities to suggestion list
                                            for (Activity newActivity : newActivities) {
                                                // Add the new activity only if it's not already present in activityDest
                                                if (!activityDest.contains(newActivity)) {
                                                    activityDest.add(newActivity);
                                                }
                                            }
                                            // Update the remaining budget
                                            activities = new ArrayList<>(activityDest);
                                            while (!activities.isEmpty() && dailyActivity == null) {
                                                int randomIdx = random.nextInt(activities.size());
                                                Activity activity = activities.get(randomIdx);
                                                if (activity.getPrice() >= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                    dailyActivity = activity;
                                                }
                                                activities.remove(randomIdx);
                                            }
                                            iterationDailyActivityAI++;
                                            // Add the chosen activity to the list of chosen activities and update the cost
                                        }
                                        if (dailyActivity != null) {
                                            cost += dailyActivity.getPrice();
                                            chosenActivities.add(dailyActivity);
                                        }
                                    }
                                    // Append the details of the daily activity to the dayActivityBuilder
                                    if (dailyActivity != null) {
                                        dayActivityBuilder.append("  Activity : ").append(dailyActivity.getActivityName()).append("\n");
                                        dayActivityBuilder.append("    Type of the activity: ").append(dailyActivity.getActivityType()).append("\n");
                                        dayActivityBuilder.append("    Description: ").append(dailyActivity.getDescription()).append("\n");
                                        dayActivityBuilder.append("    Price: ").append(dailyActivity.getPrice()).append("\n\n");

                                    } else {
                                        dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                    }
                                }
                            }
                            String dayActivity = dayActivityBuilder.toString();
                            suggestionFinal = new TripSuggestion(countryDest, cityDest, chosenActivities, survey, startingDate, endingDate, dayActivity, cost);
                            // Add the suggestion to the list of trip suggestions
                            listTripSuggestion.add(suggestionFinal);
                        }
                    }
                }
                else {

                    int day = 0;
                    long timeTrip = ChronoUnit.DAYS.between(startingDate, endingDate); // Convert the trip's date into days
                    if(timeTrip<=0){
                        throw new IllegalArgumentException("The dates you have entered are incorrect. Please check them.");
                    }
                    // Calculate the number of cities to visit during the road trip
                    int numberOfCitiesInRoadTrip = (int) Math.max(1, Math.ceil((double) timeTrip / 7));

                    // Iterate until enough cities are found or the maximum iteration count is reached
                    while(iterationCityAI < 10 && (cityList == null || cityList.size() < numberOfCitiesInRoadTrip)) {

                        //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                        if (timeLastRequest != null) {
                            long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                            if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                try {
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        timeLastRequest = Instant.now();

                        // Call the AI to add new cities based on the information on the survey
                        try {
                            jsonString = aiService.chatTripCity(countryDest, survey);
                            objectMapper = new ObjectMapper();
                            City city = objectMapper.readValue(jsonString, City.class);
                            // Add suggested city to database
                            cityService.addCity(city);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        // Retrieve updated city list
                        cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);
                        iterationCityAI++;
                    }
                    // Calculate the number of days to spend in each city
                    numberOfDaysPerCity = Math.max((int)Math.ceil((double)timeTrip/ numberOfCitiesInRoadTrip),3);
                    while (day<timeTrip) {
                        int costCity =0;
                        String dayActivity = "";
                        dayActivityBuilder = new StringBuilder();
                        if (!cityList.isEmpty()) {
                            // Select a random city in the list of city
                            int randomIndex2 = random.nextInt(cityList.size());
                            cityDest = cityList.get(randomIndex2);
                            activityDest = new ArrayList<>();
                            // Retrieve activities in the selected city based on activity types
                            for (ActivityType activityType1 : activityType) {
                                activityDest.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                            }

                            int activitiesPerDay = 2; // Number of activities per day
                            iterationActivityAI = 0;
                            // Define the upper and lower limits for the price of activities
                            Integer lowerLimit = (int)((budget/numberOfCitiesInRoadTrip)/(numberOfDaysPerCity*activitiesPerDay*2));
                            Integer upperLimit;
                            int upperLimitFactor = 3; //The higher this factor the more likely it is that the activities are costly, which means that the budget will be reached earlier and that the last activities will be free.
                            if (upperLimitFactor*lowerLimit < budget/numberOfCitiesInRoadTrip){
                                upperLimit = upperLimitFactor*lowerLimit;
                            }else{
                                upperLimit = budget/numberOfCitiesInRoadTrip;
                            }
                            // Retry until enough activities are found or the maximum iteration count is reached
                            while (activityDest.size() < numberOfDaysPerCity*activitiesPerDay && iterationActivityAI < 10) {
                                // Call the AI to add new activities based on the information on the survey


                                //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                                if (timeLastRequest != null) {
                                    long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                    if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                        long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                        try {
                                            Thread.sleep(waitTime);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                timeLastRequest = Instant.now();

                                try {
                                    jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey,upperLimit,lowerLimit);
                                    objectMapper = new ObjectMapper();

                                    City city = objectMapper.readValue(jsonString, City.class);
                                    cityService.addCity(city);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                List<Activity> newActivities = new ArrayList<>();
                                // Retrieve updated activity list for the city
                                for (ActivityType activityType1 : activityType) {
                                    newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                                }
                                // Add new activities to suggestion list
                                for (Activity newActivity : newActivities) {
                                    // Add the new activity only if it's not already present in activityDest
                                    if (!activityDest.contains(newActivity)) {
                                        activityDest.add(newActivity);
                                    }
                                }
                                iterationActivityAI++;
                            }

                            if (!activityDest.isEmpty()) {
                                List<Activity> activities = new ArrayList<>(); // Copy the list to avoid modifying the original
                                List<Activity> chosenActivities = new ArrayList<>();
                                for(Activity act : activityDest){
                                    if (act.getPrice()<=budget/numberOfCitiesInRoadTrip){
                                        activities.add(act);
                                    }
                                }
                                // A loop that assigns one activity per day based on the duration of the trip.
                                for (int j = 0; j<numberOfDaysPerCity; j++) {
                                    dayActivityBuilder.append("\n Day-").append(day + 1 ).append(" : \n\n");
                                    for (int activityIndex = 0; activityIndex < activitiesPerDay; activityIndex++) {
                                        Activity dailyActivity = null;

                                        // Define the upper and lower limit depending of the current cost of the travel suggestion
                                        int costValue = budget/numberOfCitiesInRoadTrip - costCity;
                                        if (costValue>=(budget/numberOfCitiesInRoadTrip)/(numberOfDaysPerCity*activitiesPerDay*2)){
                                            lowerLimit = (int)(budget/numberOfCitiesInRoadTrip)/(numberOfDaysPerCity*activitiesPerDay*2);
                                            if (upperLimitFactor*lowerLimit < costValue){
                                                upperLimit = upperLimitFactor*lowerLimit;
                                            }else{
                                                upperLimit = costValue;
                                            }
                                        }else{
                                            lowerLimit = 0;
                                            upperLimit = costValue;
                                        }

                                        // Retry until a suitable daily activity is found
                                        while (!activities.isEmpty() && dailyActivity==null) {
                                            int randomIdx = random.nextInt(activities.size());
                                            Activity activity = activities.get(randomIdx);
                                            if (activity.getPrice()>= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                dailyActivity= activity;
                                            }
                                            activities.remove(randomIdx);
                                        }
                                        // Retry using AI if no suitable activity is found
                                        iterationDailyActivityAI = 0;
                                        while(dailyActivity==null && iterationDailyActivityAI < 12){

                                            //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                                            if (timeLastRequest != null) {
                                                long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                                if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                                    long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                                    try {
                                                        Thread.sleep(waitTime);
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                            timeLastRequest = Instant.now();


                                            try {
                                                jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey, upperLimit, lowerLimit);
                                                objectMapper = new ObjectMapper();

                                                City city = objectMapper.readValue(jsonString, City.class);
                                                cityService.addCity(city);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            List<Activity> newActivities = new ArrayList<>();
                                            // Retrieve updated activity list for the city
                                            for (ActivityType activityType1 : activityType) {
                                                newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                                            }
                                            // Add new activities to suggestion list
                                            for (Activity newActivity : newActivities) {
                                                // Add the new activity only if it's not already present in activityDest
                                                if (!activityDest.contains(newActivity)) {
                                                    activityDest.add(newActivity);
                                                }
                                            }
                                            // Update price limits and retry activity selection
                                            activities = new ArrayList<>(activityDest);
                                            while (!activities.isEmpty() && dailyActivity==null) {
                                                int randomIdx = random.nextInt(activities.size());
                                                Activity activity = activities.get(randomIdx);
                                                if (activity.getPrice()>= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                    dailyActivity= activity;
                                                }
                                                activities.remove(randomIdx);
                                            }
                                            iterationDailyActivityAI++;
                                        }
                                        // Add the chosen activity to the list of chosen activities and update the cost
                                        if(dailyActivity!=null) {
                                            cost += dailyActivity.getPrice();
                                            costCity += dailyActivity.getPrice();
                                            chosenActivities.add(dailyActivity);
                                        }
                                        if (dailyActivity!=null) {
                                            dayActivityBuilder.append("  Activity : ").append(dailyActivity.getActivityName()).append("\n");
                                            dayActivityBuilder.append("    Type of the activity: ").append(dailyActivity.getActivityType()).append("\n");
                                            dayActivityBuilder.append("    Description: ").append(dailyActivity.getDescription()).append("\n");
                                            dayActivityBuilder.append("    Price: ").append(dailyActivity.getPrice()).append("\n\n");
                                        } else {
                                            dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                        }
                                    }
                                    day++;
                                    if(day>=timeTrip){
                                        break;
                                    }
                                }
                                dayActivity = dayActivityBuilder.toString();
                                suggestionFinal = new TripSuggestion(countryDest, cityDest, chosenActivities, survey, startingDate, endingDate, dayActivity, cost, costCity);
                                // Add the suggestion to the list of trip suggestions
                                listTripSuggestion.add(suggestionFinal);
                            }else{
                                dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                day++;
                            }

                            // Remove the selected city to avoid duplicates
                            cityList.remove(randomIndex2);
                        }else{
                            break;
                        }
                    }
                }
            }
        }else{
            throw new IllegalArgumentException("The localisation you have given in the survey is incorrect, please check the survey.");
        }
        if(!listTripSuggestion.isEmpty()) {
            cacheConfig.putTripSuggestionInCache(survey, listTripSuggestion);
        }
        // Return the list of suggestions
        return listTripSuggestion;
    }

    // Provide a suggestion that hasn't been previously provided and isn't stored in the cache.
    @SneakyThrows
    public List<TripSuggestion> getSuggestionAIWithCache(TripSurvey survey) throws IllegalArgumentException{

        // Extract survey parameters
        // Correct the localisation given by the user to avoid creating errors in the database related to typing issues
        if(Continent.isValidValue(survey.getLocalisation().toUpperCase().replace(" ", "_"))){
            survey.setLocalisation(survey.getLocalisation().toUpperCase().replace(" ", "_"));
        }else if (!survey.getLocalisation().equals("ALL")) {
            String localisation = survey.getLocalisation().toLowerCase().replace("_", " ");
            // Capitalizes the first letter of the location
            localisation = Character.toUpperCase(localisation.charAt(0)) + localisation.substring(1);
            // Capitalizes the first letter after each space in the location
            for (int i = 0; i < localisation.length(); i++) {
                if (localisation.charAt(i) == ' ') {
                    localisation = localisation.substring(0, i + 1) + Character.toUpperCase(localisation.charAt(i + 1)) + localisation.substring(i + 2);
                }
            }
            survey.setLocalisation(localisation);
        }
        String localisation = survey.getLocalisation();
        Climate climate = survey.getClimate();
        Boolean roadTrip = survey.isRoadTrip();
        Landscape landscape = survey.getLandscape();
        Temperature temperature = survey.getTemperature();
        List<ActivityType> activityType = survey.getActivityType();
        // Parsing departure and arrival dates into LocalDate
        LocalDate startingDate = LocalDate.parse(survey.getStartingDate());
        LocalDate endingDate = LocalDate.parse(survey.getEndingDate());
        int budget = survey.getBudget();

        // Initializing variables
        Country countryDest = null;
        TripSuggestion suggestionFinal;
        List<Country> countryList = new ArrayList<>();
        List<City> cityList;
        Random random;
        int randomIndex;
        int numberOfDaysPerCity;
        City cityDest;
        List<Activity> activityDest;
        int cost = 0;
        StringBuilder dayActivityBuilder = new StringBuilder();
        List<TripSuggestion> listTripSuggestion = new ArrayList<>();
        random = new Random();
        String jsonString;
        ObjectMapper objectMapper;
        int iterationCountryAI = 0;
        int iterationCityAI = 0;
        int iterationActivityAI = 0;
        int iterationDailyActivityAI = 0;

        List<List<TripSuggestion>> suggestionsFromCache = cacheConfig.getTripSuggestionFromCache(survey);

        // Gets the value of the request per minute of the api key from an environment variable which is defined by the user prior to the launch
        int intValue = 3500;
        try {
            String maVariableEnv = System.getenv("RPM_API_KEY");
            intValue = Integer.parseInt(maVariableEnv);
        } catch (NumberFormatException e) {
            System.out.println("The environment variable RPM_API_KEY must be defined and must be an integer.");
        }

        //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
        int numberOfRequestPerMinute = intValue;
        long minMillisecondBetweenRequest = (60/numberOfRequestPerMinute)*1000;
        Instant timeLastRequest = null;

        // Generate suggestions based on survey criteria
        if (localisation.equals("ALL")||Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))||RecognizedCountry.isValidValue(localisation.toUpperCase().replace(" ", "_"))){
            // Manage the code for both cases as the code is almost the same for the two, only the initialisation of countryList change
            if (localisation.equals("ALL")||Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) {
                // No specific country specified by the user, the function will return 1 to 3 suggestions of different countries
                if(localisation.equals("ALL")){
                    // Retrieve list of countries based on the Climate in the survey
                    if (climate.equals(Climate.ALL)) {
                        countryList = countryRepository.findAll();
                    } else {
                        countryList = countryService.findAllByClimateListContaining(climate);
                    }
                }else if(Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) {
                    // A specific continent is given by the user, the function will return 1 to 3 suggestions of different countries in the specified continent
                    // Handle an error where the AI return erroneous values
                    if(survey.getClimate() == Climate.MEDITERRANEAN && !survey.getLocalisation().equals(Continent.EUROPE.toString()) && !survey.getLocalisation().equals(Continent.AFRICA.toString())){
                        survey.setClimate(Climate.TEMPERATE);
                        climate = Climate.TEMPERATE;
                        suggestionsFromCache = cacheConfig.getTripSuggestionFromCache(survey);
                    }

                    // Retrieve the specified country based on the given continent and climate
                    if (climate.equals(Climate.ALL)) {
                        countryList = countryRepository.findAllByContinent(Continent.valueOf(localisation.toUpperCase()));
                    } else {
                        countryList = countryService.findCountryByContinentAndClimateListContaining(Continent.valueOf(localisation.toUpperCase()), climate);
                    }

                    //Check in the cache how many different countries have been given for the continent and if it is under the possible number of countries for this continent. If we ask for more countries than they are in the continent the AI may send incorrect data.
                    //So we send back one of the different suggestions in the cache
                    if(suggestionsFromCache!=null&&!suggestionsFromCache.isEmpty()) {
                        if ((suggestionsFromCache.size()+1) > Continent.valueOf(localisation.toUpperCase()).getNumberOfCountries()){
                            List<TripSuggestion> randomSuggestionFromCache = suggestionsFromCache.get(random.nextInt(suggestionsFromCache.size()));
                            return randomSuggestionFromCache;
                        }
                    }

                }

                // Remove the countries which have been given in previous suggestions
                if(suggestionsFromCache != null) {
                    for(List<TripSuggestion> suggestionFromCache : suggestionsFromCache) {
                        for (TripSuggestion tripSuggestion : suggestionFromCache) {
                            Country country = tripSuggestion.getCountry();
                            if (country != null && countryList.contains(country)) {
                                countryList.remove(country);
                            }
                        }
                    }
                }

                if(!countryList.isEmpty()) {
                    // Select a random country in the list of country
                    randomIndex = random.nextInt(countryList.size());
                    countryDest = countryList.get(randomIndex);
                }

                // Add suggested countries to the list and retry if the list is empty
                while (countryList.isEmpty() && iterationCountryAI<10) {
                    try {
                        objectMapper = new ObjectMapper();
                        jsonString = aiService.chatTripCountry(survey);
                        Country country = objectMapper.readValue(jsonString, Country.class);
                        // Add the new country to the database
                        countryService.addCountry(country);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // Retrieve updated country list
                    if (localisation.equals("ALL")) {
                        if (climate.equals(Climate.ALL)) {
                            countryList = countryRepository.findAll();
                        } else {
                            countryList = countryService.findAllByClimateListContaining(climate);
                        }
                    } else if (Continent.isValidValue(localisation.toUpperCase().replace(" ", "_"))) {
                        if (climate.equals(Climate.ALL)) {
                            countryList = countryRepository.findAllByContinent(Continent.valueOf(localisation.toUpperCase().replace(" ", "_")));

                        } else {
                            countryList = countryService.findCountryByContinentAndClimateListContaining(Continent.valueOf(localisation.toUpperCase().replace(" ", "_")), climate);
                        }
                    }
                    iterationCountryAI++;

                    // Remove the countries which have been given in previous suggestions
                    if(suggestionsFromCache != null) {
                        for(List<TripSuggestion> suggestionFromCache : suggestionsFromCache) {
                            for (TripSuggestion tripSuggestion : suggestionFromCache) {
                                Country country = tripSuggestion.getCountry();
                                if (country != null && countryList.contains(country)) {
                                    countryList.remove(country);
                                }
                            }
                        }
                    }

                }
                if(!countryList.isEmpty()) {
                    // Select a random country from the updated list
                    randomIndex = random.nextInt(countryList.size());
                    countryDest = countryList.get(randomIndex);
                }

            }else if(RecognizedCountry.isValidValue(localisation.toUpperCase().replace(" ", "_"))){
                // Retrieve the specified country based on the given name and climate from the database
                if(climate.equals(Climate.ALL)){
                    countryDest = countryRepository.findByCountryName(localisation);
                }else{
                    countryDest = countryService.findCountryByCountryNameAndClimateListContaining(localisation, climate);
                }

                // Retry until the specified country is given by the AI
                while(countryDest==null && iterationCountryAI<3) {
                    // Call the AI to add the new country based on the information on the survey
                    try{
                        objectMapper = new ObjectMapper();
                        jsonString = aiService.chatTripCountry(survey);
                        Country country = objectMapper.readValue(jsonString, Country.class);
                        // Handle an error where the AI return erroneous values
                        if(survey.getClimate() == Climate.MEDITERRANEAN && country.getContinent() != Continent.EUROPE && country.getContinent() != Continent.AFRICA){
                            survey.setClimate(Climate.TEMPERATE);
                            climate = Climate.TEMPERATE;
                            suggestionsFromCache = cacheConfig.getTripSuggestionFromCache(survey);
                            throw new IllegalArgumentException("MEDITERRANEAN Climate asked for a continent with no MEDITERRANEAN climate, Correction : The climate is changed from MEDITERRANEAN to TEMPERATE");
                        }
                        // Add suggested country to database
                        countryService.addCountry(country);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                    if(climate.equals(Climate.ALL)){
                        countryDest = countryRepository.findByCountryName(localisation);
                    } else {
                        countryDest = countryService.findCountryByCountryNameAndClimateListContaining(localisation, climate);
                    }
                    iterationCountryAI++;
                }

            }

            // Remove the cities which have been given in previous suggestions
            if(countryDest!=null){
                // Filter cities in the selected country based on criteria
                cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);

                if(suggestionsFromCache != null) {
                    for(List<TripSuggestion> suggestionFromCache : suggestionsFromCache) {
                        for (TripSuggestion tripSuggestion : suggestionFromCache) {
                            City city = tripSuggestion.getCity();
                            if (city != null && cityList.contains(city)) {
                                cityList.remove(city);
                            }
                        }
                    }
                }

                // Verify if the user want a suggestion for travel through multiple cities or not
                if (!roadTrip) {
                    // Retry until enough cities are found or a maximum iteration count is reached
                    while(( cityList == null || cityList.isEmpty()) && iterationCityAI < 10) {

                        //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                        if (timeLastRequest != null) {
                            long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                            if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                try {
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        timeLastRequest = Instant.now();


                        // Call the AI to add new cities based on the information on the survey
                        try {
                            jsonString = aiService.chatTripCity(countryDest, survey);
                            objectMapper = new ObjectMapper();
                            City city = objectMapper.readValue(jsonString, City.class);
                            // Add suggested city to database
                            cityService.addCity(city);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        // Retrieve updated city list
                        cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);
                        iterationCityAI++;

                        // Remove the cities which have been given in previous suggestions
                        if(suggestionsFromCache != null) {
                            for(List<TripSuggestion> suggestionFromCache : suggestionsFromCache) {
                                for (TripSuggestion tripSuggestion : suggestionFromCache) {
                                    City city = tripSuggestion.getCity();
                                    if (city != null && cityList.contains(city)) {
                                        cityList.remove(city);
                                    }
                                }
                            }
                        }
                    }

                    if(cityList!=null && !cityList.isEmpty()) {
                        // Select a random city in the list of city
                        int randomIndex2 = random.nextInt(cityList.size());
                        cityDest = cityList.get(randomIndex2);
                        activityDest = new ArrayList<>();
                        // Retrieve activities in the selected city based on activity types
                        for (ActivityType activityType1 : activityType) {
                            activityDest.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                        }

                        long timeTrip = ChronoUnit.DAYS.between(startingDate, endingDate); // Convert the trip's date into days
                        int activitiesPerDay = 2; // Number of activities per day

                        // Define the upper and lower limit for the price of the activities
                        Integer lowerLimit = (int) (budget / (timeTrip * activitiesPerDay * 2));
                        Integer upperLimit;
                        int upperLimitFactor = 3; //The higher this factor the more likely it is that the activities are costly, which means that the budget will be reached earlier and that the last activities will be free.
                        if (upperLimitFactor * lowerLimit < budget) {
                            upperLimit = upperLimitFactor * lowerLimit;
                        } else {
                            upperLimit = budget;
                        }

                        if (timeTrip <= 0) {
                            throw new IllegalArgumentException("The dates you have entered are incorrect. Please check them.");
                        }

                        // Retry until enough activities are found or a maximum iteration count is reached
                        while ((activityDest == null || activityDest.size() < timeTrip * activitiesPerDay) && iterationActivityAI < 10) {

                            //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                            if (timeLastRequest != null) {
                                long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                    long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                    try {
                                        Thread.sleep(waitTime);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            timeLastRequest = Instant.now();


                            try {
                                jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey, upperLimit, lowerLimit);
                                objectMapper = new ObjectMapper();

                                City city = objectMapper.readValue(jsonString, City.class);
                                cityService.addCity(city);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                            List<Activity> newActivities = new ArrayList<>();
                            // Retrieve updated activity list for the city
                            for (ActivityType activityType1 : activityType) {
                                newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                            }
                            // Add new activities to suggestion list
                            for (Activity newActivity : newActivities) {
                                // Add the new activity only if it's not already present in activityDest
                                if (!activityDest.contains(newActivity)) {
                                    activityDest.add(newActivity);
                                }
                            }
                            iterationActivityAI++;
                        }
                        //  Extends the activity selection logic for each day of the trip by adding the ability to suggest activities in real time using an artificial intelligence service
                        if (!activityDest.isEmpty()) {
                            List<Activity> chosenActivities = new ArrayList<>();
                            List<Activity> activities;
                            // A loop that assigns one activity per day based on the duration of the trip.
                            for (int day = 0; day < timeTrip; day++) {
                                dayActivityBuilder.append("\n Day-").append(day + 1).append(" : \n\n");
                                for (int activityIndex = 0; activityIndex < activitiesPerDay; activityIndex++) {
                                    Activity dailyActivity = null;
                                    if (!activityDest.isEmpty()) {
                                        int costValue = budget - cost;
                                        activities = new ArrayList<>(activityDest);
                                        // Adjust upper and lower limits based on remaining budget
                                        if (costValue >= (budget) / (timeTrip * activitiesPerDay)) {
                                            lowerLimit = (int) (budget / (timeTrip * activitiesPerDay * 2));
                                            if (upperLimitFactor * lowerLimit < costValue) {
                                                upperLimit = upperLimitFactor * lowerLimit;
                                            } else {
                                                upperLimit = costValue;
                                            }
                                        } else {
                                            lowerLimit = 0;
                                            upperLimit = costValue;
                                        }
                                        // Retry until a suitable daily activity is found
                                        while (!activities.isEmpty() && dailyActivity == null) {
                                            int randomIdx = random.nextInt(activities.size());
                                            Activity activity = activities.get(randomIdx);
                                            if (activity.getPrice() >= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                dailyActivity = activity;
                                            }
                                            activities.remove(randomIdx);
                                        }
                                        iterationDailyActivityAI = 0;
                                        while (dailyActivity == null && iterationDailyActivityAI < 10) {

                                            //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                                            if (timeLastRequest != null) {
                                                long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                                if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                                    long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                                    try {
                                                        Thread.sleep(waitTime);
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                            timeLastRequest = Instant.now();

                                            try {
                                                jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey, upperLimit, lowerLimit);
                                                objectMapper = new ObjectMapper();

                                                City city = objectMapper.readValue(jsonString, City.class);
                                                cityService.addCity(city);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            List<Activity> newActivities = new ArrayList<>();
                                            // Retrieve updated activity list for the city
                                            for (ActivityType activityType1 : activityType) {
                                                newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                                            }
                                            // Add new activities to suggestion list
                                            for (Activity newActivity : newActivities) {
                                                // Add the new activity only if it's not already present in activityDest
                                                if (!activityDest.contains(newActivity)) {
                                                    activityDest.add(newActivity);
                                                }
                                            }
                                            // Update the remaining budget
                                            costValue = budget - cost;
                                            activities = new ArrayList<>(activityDest);
                                            while (!activities.isEmpty() && dailyActivity == null) {
                                                int randomIdx = random.nextInt(activities.size());
                                                Activity activity = activities.get(randomIdx);
                                                if (activity.getPrice() >= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                    dailyActivity = activity;
                                                }
                                                activities.remove(randomIdx);
                                            }
                                            iterationDailyActivityAI++;
                                            // Add the chosen activity to the list of chosen activities and update the cost
                                        }
                                        if (dailyActivity != null) {
                                            cost += dailyActivity.getPrice();
                                            chosenActivities.add(dailyActivity);
                                        }
                                    }
                                    // Append the details of the daily activity to the dayActivityBuilder
                                    if (dailyActivity != null) {
                                        dayActivityBuilder.append("  Activity : ").append(dailyActivity.getActivityName()).append("\n");
                                        dayActivityBuilder.append("    Type of the activity: ").append(dailyActivity.getActivityType()).append("\n");
                                        dayActivityBuilder.append("    Description: ").append(dailyActivity.getDescription()).append("\n");
                                        dayActivityBuilder.append("    Price: ").append(dailyActivity.getPrice()).append("\n\n");

                                    } else {
                                        dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                    }
                                }
                            }
                            String dayActivity = dayActivityBuilder.toString();
                            suggestionFinal = new TripSuggestion(countryDest, cityDest, chosenActivities, survey, startingDate, endingDate, dayActivity, cost);
                            // Add the suggestion to the list of trip suggestions
                            listTripSuggestion.add(suggestionFinal);
                        }
                    }
                } else {

                    int day = 0;
                    long timeTrip = ChronoUnit.DAYS.between(startingDate, endingDate); // Convert the trip's date into days
                    if(timeTrip<=0){
                        throw new IllegalArgumentException("The dates you have entered are incorrect. Please check them.");
                    }
                    // Calculate the number of cities to visit during the road trip
                    int numberOfCitiesInRoadTrip = (int) Math.max(1, Math.ceil((double) timeTrip / 7));

                    if(suggestionsFromCache != null) {
                        for(List<TripSuggestion> suggestionFromCache : suggestionsFromCache) {
                            for (TripSuggestion tripSuggestion : suggestionFromCache) {
                                City city = tripSuggestion.getCity();
                                if (city != null && cityList.contains(city)) {
                                    cityList.remove(city);
                                }
                            }
                        }
                    }

                    iterationCityAI = 0;
                    // Iterate until enough cities are found or the maximum iteration count is reached
                    while(iterationCityAI < 10 && ( cityList == null || cityList.size() < numberOfCitiesInRoadTrip)) {

                        //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                        if (timeLastRequest != null) {
                            long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                            if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                try {
                                    Thread.sleep(waitTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                        timeLastRequest = Instant.now();

                        // Call the AI to add new cities based on the information on the survey
                        try {
                            jsonString = aiService.chatTripCity(countryDest, survey);
                            objectMapper = new ObjectMapper();
                            City city = objectMapper.readValue(jsonString, City.class);
                            // Add suggested city to database
                            cityService.addCity(city);
                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        // Retrieve updated city list
                        cityList = cityService.filterCitiesByCriteria(countryDest, landscape, temperature, climate);
                        iterationCityAI++;

                        if(suggestionsFromCache != null) {
                            for(List<TripSuggestion> suggestionFromCache : suggestionsFromCache) {
                                for (TripSuggestion tripSuggestion : suggestionFromCache) {
                                    City city = tripSuggestion.getCity();
                                    if (city != null && cityList.contains(city)) {
                                        cityList.remove(city);
                                    }
                                }
                            }
                        }
                    }
                    // Calculate the number of days to spend in each city
                    numberOfDaysPerCity = Math.max((int)Math.ceil((double)timeTrip/ numberOfCitiesInRoadTrip),1);
                    while (day<timeTrip) {
                        int costCity =0;
                        String dayActivity = "";
                        dayActivityBuilder = new StringBuilder();
                        if (!cityList.isEmpty()) {
                            // Select a random city in the list of city
                            int randomIndex2 = random.nextInt(cityList.size());
                            cityDest = cityList.get(randomIndex2);
                            activityDest = new ArrayList<>();
                            // Retrieve activities in the selected city based on activity types
                            for (ActivityType activityType1 : activityType) {
                                activityDest.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                            }

                            int activitiesPerDay = 2; // Number of activities per day
                            iterationActivityAI = 0;
                            // Define the upper and lower limits for the price of activities
                            Integer lowerLimit = (int)((budget/numberOfCitiesInRoadTrip)/(numberOfDaysPerCity*activitiesPerDay*2));
                            Integer upperLimit;
                            int upperLimitFactor = 3; //The higher this factor the more likely it is that the activities are costly, which means that the budget will be reached earlier and that the last activities will be free.
                            if (upperLimitFactor*lowerLimit < budget/numberOfCitiesInRoadTrip){
                                upperLimit = upperLimitFactor*lowerLimit;
                            }else{
                                upperLimit = budget/numberOfCitiesInRoadTrip;
                            }
                            // Retry until enough activities are found or the maximum iteration count is reached
                            while ((activityDest == null || activityDest.size() < numberOfDaysPerCity*activitiesPerDay ) && iterationActivityAI < 10) {
                                // Call the AI to add new activities based on the information on the survey

                                //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                                if (timeLastRequest != null) {
                                    long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                    if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                        long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                        try {
                                            Thread.sleep(waitTime);
                                        } catch (InterruptedException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                                timeLastRequest = Instant.now();

                                try {
                                    jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey,upperLimit,lowerLimit);
                                    objectMapper = new ObjectMapper();

                                    City city = objectMapper.readValue(jsonString, City.class);
                                    cityService.addCity(city);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                List<Activity> newActivities = new ArrayList<>();
                                // Retrieve updated activity list for the city
                                for (ActivityType activityType1 : activityType) {
                                    newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                                }
                                // Add new activities to suggestion list
                                for (Activity newActivity : newActivities) {
                                    // Add the new activity only if it's not already present in activityDest
                                    if (!activityDest.contains(newActivity)) {
                                        activityDest.add(newActivity);
                                    }
                                }
                                iterationActivityAI++;
                            }

                            if (!activityDest.isEmpty()) {
                                List<Activity> activities = new ArrayList<>(); // Copy the list to avoid modifying the original
                                List<Activity> chosenActivities = new ArrayList<>();
                                for(Activity act : activityDest){
                                    if (act.getPrice()<=budget/numberOfCitiesInRoadTrip){
                                        activities.add(act);
                                    }
                                }
                                // A loop that assigns one activity per day based on the duration of the trip.
                                for (int j = 0; j<numberOfDaysPerCity; j++) {
                                    dayActivityBuilder.append("\n Day-").append(day + 1 ).append(" : \n\n");
                                    for (int activityIndex = 0; activityIndex < activitiesPerDay; activityIndex++) {
                                        Activity dailyActivity = null;

                                        // Define the upper and lower limit depending of the current cost of the travel suggestion
                                        int costValue = budget/numberOfCitiesInRoadTrip - costCity;
                                        if (costValue>=(budget/numberOfCitiesInRoadTrip)/(numberOfDaysPerCity*activitiesPerDay*2)){
                                            lowerLimit = (int)(budget/numberOfCitiesInRoadTrip)/(numberOfDaysPerCity*activitiesPerDay*2);
                                            if (upperLimitFactor*lowerLimit < costValue){
                                                upperLimit = upperLimitFactor*lowerLimit;
                                            }else{
                                                upperLimit = costValue;
                                            }
                                        }else{
                                            lowerLimit = 0;
                                            upperLimit = costValue;
                                        }

                                        // Retry until a suitable daily activity is found
                                        while (!activities.isEmpty() && dailyActivity==null) {
                                            int randomIdx = random.nextInt(activities.size());
                                            Activity activity = activities.get(randomIdx);
                                            if (activity.getPrice()>= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                dailyActivity= activity;
                                            }
                                            activities.remove(randomIdx);
                                        }
                                        // Retry using AI if no suitable activity is found
                                        iterationDailyActivityAI = 0;
                                        while(dailyActivity==null && iterationDailyActivityAI < 12){


                                            //If you have a low RPM on your AI API Key, this prevents from sending too many requests to the AI
                                            if (timeLastRequest != null) {
                                                long millisecondBetweenRequest = millisecondsSince(timeLastRequest);
                                                if (millisecondBetweenRequest < minMillisecondBetweenRequest) {
                                                    long waitTime = (minMillisecondBetweenRequest - millisecondBetweenRequest);
                                                    try {
                                                        Thread.sleep(waitTime);
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                            }
                                            timeLastRequest = Instant.now();

                                            try {
                                                jsonString = aiService.chatTripActivity(countryDest, cityDest, activityDest, survey, upperLimit, lowerLimit);
                                                objectMapper = new ObjectMapper();

                                                City city = objectMapper.readValue(jsonString, City.class);
                                                cityService.addCity(city);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                            List<Activity> newActivities = new ArrayList<>();
                                            // Retrieve updated activity list for the city
                                            for (ActivityType activityType1 : activityType) {
                                                newActivities.addAll(activityRepository.findAllByCityAndActivityType(cityDest, activityType1));
                                            }
                                            // Add new activities to suggestion list
                                            for (Activity newActivity : newActivities) {
                                                // Add the new activity only if it's not already present in activityDest
                                                if (!activityDest.contains(newActivity)) {
                                                    activityDest.add(newActivity);
                                                }
                                            }
                                            // Update price limits and retry activity selection
                                            activities = new ArrayList<>(activityDest);
                                            while (!activities.isEmpty() && dailyActivity==null) {
                                                int randomIdx = random.nextInt(activities.size());
                                                Activity activity = activities.get(randomIdx);
                                                if (activity.getPrice()>= lowerLimit && activity.getPrice() <= upperLimit && !chosenActivities.contains(activity)) {
                                                    dailyActivity= activity;
                                                }
                                                activities.remove(randomIdx);
                                            }
                                            iterationDailyActivityAI++;
                                        }
                                        // Add the chosen activity to the list of chosen activities and update the cost
                                        if(dailyActivity!=null) {
                                            cost += dailyActivity.getPrice();
                                            costCity += dailyActivity.getPrice();
                                            chosenActivities.add(dailyActivity);
                                        }
                                        if (dailyActivity!=null) {
                                            dayActivityBuilder.append("  Activity : ").append(dailyActivity.getActivityName()).append("\n");
                                            dayActivityBuilder.append("    Type of the activity: ").append(dailyActivity.getActivityType()).append("\n");
                                            dayActivityBuilder.append("    Description: ").append(dailyActivity.getDescription()).append("\n");
                                            dayActivityBuilder.append("    Price: ").append(dailyActivity.getPrice()).append("\n\n");
                                        } else {
                                            dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                        }
                                    }
                                    day++;
                                    if(day>=timeTrip){
                                        break;
                                    }
                                }
                                dayActivity = dayActivityBuilder.toString();
                                suggestionFinal = new TripSuggestion(countryDest, cityDest, chosenActivities, survey, startingDate, endingDate, dayActivity, cost, costCity);
                                // Add the suggestion to the list of trip suggestions
                                listTripSuggestion.add(suggestionFinal);
                            }else{
                                dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                dayActivityBuilder.append("  No activities found for the given settings").append("\n\n");
                                day++;
                            }

                            // Remove the selected city to avoid duplicates
                            cityList.remove(randomIndex2);
                        }else{
                            break;
                        }
                    }
                }
            }
        }else{
            throw new IllegalArgumentException("The localisation you have given in the survey is incorrect, please check the survey.");
        }
        if(!listTripSuggestion.isEmpty()) {
            cacheConfig.putTripSuggestionInCache(survey, listTripSuggestion);
        }

        // Return the list of suggestions
        return listTripSuggestion;
    }

    //Calculates the number of milliseconds elapsed since a given instant.
    private long millisecondsSince(Instant start) {
        Instant end = Instant.now();
        return end.toEpochMilli()-start.toEpochMilli();
    }


}