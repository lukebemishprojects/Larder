package dev.lukebemish.larder;

import io.javalin.Javalin;

public class Larder {
    static void main(String[] args) {
        var app = Javalin.create()
                .start(8786);
    }
}
