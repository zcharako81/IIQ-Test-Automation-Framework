package tests.base;

import io.restassured.RestAssured;

import org.testng.annotations.BeforeClass;

import base.ConfigManager;

public class BaseTest {

    @BeforeClass

    public void setup() {

        RestAssured.baseURI = ConfigManager.get("base.url");

    }

}