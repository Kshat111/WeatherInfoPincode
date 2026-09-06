package com.example.weather.adapters;

public interface OpenWeatherClient {
    GeocodeResult geocodeByPincode(String pincode);
    String getCurrentWeather(double latitude, double longitude);
    String getHistoricalWeather(double latitude, double longitude, long unixTimestamp);
}
