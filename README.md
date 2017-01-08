Weather Service
====================

A couple of Services that sync every couple of hours with the latest weather in the user's location.


Where to Download
-----------------
```groovy
dependencies {
  compile 'com.xlythe:weather-service:1.0.3'
}
```

Permissions
-----------
The following permissions are required in your AndroidManfiest.xml
```xml
<!-- For open weather or weather underground -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- For awareness -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<meta-data
    android:name="com.google.android.awareness.API_KEY"
    android:value="YOUR_API_KEY" />

```

Scheduling
----------
Once the required permissions have been obtained, schedule via the following commands.
```java
AwarenessWeatherService.schedule(this);
```
```java
OpenWeatherService.schedule(this, API_KEY);
```
```java
WeatherUndergroundService.schedule(this, API_KEY);
```

How to use
----------
After a few hours, there should be data. Load it via the load commands.
```java
Weather weather = new AwarenessWeather();
weather.restore(context);
weather.getCelsius();
```


License
-------

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
