package com.kinnovatio.f1.model;

public record Meeting(int key, String name, int number, Circuit circuit, Country country,
                      String location, String officialName) {
}
