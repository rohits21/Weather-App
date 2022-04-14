package com.example.wheatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.LocationManager

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import com.example.wheatherapp.Models.WeatherResponse
import com.example.wheatherapp.databinding.ActivityMainBinding
import com.example.wheatherapp.network.WeatherService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var myFusedLocationClient: FusedLocationProviderClient
    private var progressDialog : Dialog?= null
    lateinit var mySharedPreferences: SharedPreferences


    private var binding : ActivityMainBinding ?= null


    @RequiresApi(Build.VERSION_CODES.N)
    val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {

            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                requestNewLocationData()
            } else -> {
            Toast.makeText(this, "Location Access Denied!!", Toast.LENGTH_SHORT).show()
        }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding?.root)
        mySharedPreferences = getSharedPreferences(Constants.preference_name,Context.MODE_PRIVATE)

        myFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setUpUi()

        if(!isLocationEnabled()){
            Toast.makeText(this, "Location permission denied Please Allow", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }

        locationPermissionRequest.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
        requestNewLocationData()
    }

    private val locationCallback = object : LocationCallback(){
        override fun onLocationResult(locationResult: LocationResult) {
            val myLastLocation = locationResult.lastLocation
            val latitude = myLastLocation.latitude
           // binding?.latitude?.text = latitude.toString()

            val longitude = myLastLocation.longitude
           // binding?.longitude?.text = myLongitue.toString()
            getLocationWeatherDetails(latitude,longitude)
        }

    }


    @SuppressLint("MissingPermission")
    private fun requestNewLocationData(){
        val myLocationRequest = com.google.android.gms.location.LocationRequest()
        myLocationRequest.priority = PRIORITY_HIGH_ACCURACY
        myLocationRequest.interval = 1000
        myLocationRequest.numUpdates = 1

        myFusedLocationClient.requestLocationUpdates(myLocationRequest,locationCallback,Looper.myLooper())

    }





    private fun getLocationWeatherDetails(mylatitude:Double, myLongitue:Double){
        if(Constants.isNetworkAvailable(this)){

            val retrofit : Retrofit = Retrofit.Builder().baseUrl(Constants.Base_url)
                .addConverterFactory(GsonConverterFactory.create()).build()

            val service : WeatherService = retrofit.create<WeatherService>(WeatherService::class.java)
            val listCall : Call<WeatherResponse> = service.getWeather(mylatitude,myLongitue,Constants.Metric_Unit,Constants.App_Id)

            showCustomDialog()
            listCall.enqueue(object : Callback<WeatherResponse>{
                @SuppressLint("CommitPrefEdits")
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if(response.isSuccessful){
                        hideProgressDialog()
                        val weatherList : WeatherResponse? = response.body()
                        val weatherResponseJsonSTring = Gson().toJson(weatherList)
                        val editor = mySharedPreferences.edit()
                        editor.putString(Constants.weather_response_data,weatherResponseJsonSTring)
                        editor.commit()
                            setUpUi()

                        Log.i("responseResult",weatherList.toString())
                    }else{
                        Log.i("responseCode",response.code().toString())
                    }
                }
                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error",t.message.toString())
                    hideProgressDialog()
                }
            })
        }
        else{
            Toast.makeText(this, "No Internet Connection Available", Toast.LENGTH_SHORT).show()
        }

    }

    private fun isLocationEnabled():Boolean{
        val locationManager : LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomDialog(){
        progressDialog = Dialog(this)
        progressDialog!!.setContentView(R.layout.custom_progress_dialog)
        progressDialog?.show()
    }

    private fun hideProgressDialog(){
        if(progressDialog != null){
            progressDialog?.dismiss()
        }
    }

    private fun setUpUi(){

        val weatherResponseJsonString = mySharedPreferences.getString(Constants.weather_response_data,"")

        if(!weatherResponseJsonString.isNullOrEmpty()){
            Toast.makeText(this, "Success", Toast.LENGTH_SHORT).show()
            val weatherList = Gson().fromJson(weatherResponseJsonString,WeatherResponse::class.java)

            for(i in weatherList.weather.indices){
                val weather = weatherList.weather[i]
                binding?.tvMain?.text = weather.main
                binding?.tvMainDescription?.text = weather.description
                setRightimg(weather.icon)
            }

            val main = weatherList.main
            binding?.tvTemp?.text = main.temp.toString()
            binding?.tvHumidity?.text = main.humidity.toString()
            binding?.tvMin?.text = "Min " + main.temp_min.toString()
            binding?.tvMax?.text = main.temp_max.toString() + "max"

            val wind = weatherList.wind
            binding?.tvSpeed?.text = wind.speed.toString()
            binding?.tvName?.text = weatherList.name

            val sys = weatherList.sys
            binding?.tvCountry?.text = sys.country
            binding?.tvSunriseTime?.text = unixTime(sys.sunrise)
            binding?.tvSunsetTime?.text = unixTime(sys.sunset)

        }





    }

    private fun unixTime(timex : Double):String{
        val time : Long = timex.toLong()
        val date = Date(time *1000L)
        val sdf = SimpleDateFormat("HH:mm",Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }

    private fun setRightimg(icon : String){
        when(icon){
            "01d" -> binding?.ivMain?.setImageResource(R.drawable.sunny)
            "02d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
            "03d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
            "04d" -> binding?.ivMain?.setImageResource(R.drawable.cloud)
            "10d" -> binding?.ivMain?.setImageResource(R.drawable.rain)
            "11d" -> binding?.ivMain?.setImageResource(R.drawable.storm)
            "13d" -> binding?.ivMain?.setImageResource(R.drawable.snowflake)
           else-> binding?.ivMain?.setImageResource(R.drawable.cloud)

        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main,menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        return when(item.itemId){
            R.id.action_refresh->{
                requestNewLocationData()
                true
            }else->super.onOptionsItemSelected(item)

        }

    }

}


