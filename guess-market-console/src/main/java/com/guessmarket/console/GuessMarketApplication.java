package com.guessmarket.console;

import com.guessmarket.api.GuessMarketEngine;
import com.guessmarket.engine.GuessMarketEngineImpl;

import java.util.Scanner;

public final class GuessMarketApplication {
    private GuessMarketApplication() {
    }

    public static void main(String[] args) {
        GuessMarketEngine engine = new GuessMarketEngineImpl();
        Scanner scanner = new Scanner(System.in);
        new GuessMarketConsole(engine, scanner, System.out).run();
    }
}

