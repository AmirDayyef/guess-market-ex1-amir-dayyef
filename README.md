# Guess Market - Exercises 2 and 3

A Java 25 implementation of a two-option prediction market. Exercise 3 adds a shared Tomcat server and a separate JavaFX HTTP client to the Exercise 2 engine and standalone UI.

## Participants

- Amir Dayyef - ID `323092023` - [amirdayyef@gmail.com](mailto:amirdayyef@gmail.com)
- Nour Guty - ID `322235516` - [nourguty581@gmail.com](mailto:nourguty581@gmail.com)

## Project structure

- `guess-market-api` - the UI-independent engine interface, immutable DTO records, enums, and public exceptions.
- `guess-market-engine` - XML loading and validation, accounts, LMSR pricing, order matching, peer minting, commissions, and settlement.
- `guess-market-javafx` - the JavaFX application, background XML-loading task, event and user screens, filters, tables, and trading controls.
- `guess-market-server` - the Exercise 3 in-memory HTTP market, unique-name sessions, and XML upload endpoint, packaged as a Tomcat 9 WAR.
- `guess-market-client` - the Exercise 3 JavaFX HTTP client, packaged with its runtime JAR dependencies and `run.bat`.
- `guess-market-console` - the preserved Exercise 1 console module. It is not part of the Exercise 2 Maven reactor or submission.

The Exercise 2 JavaFX module communicates with the engine only through `GuessMarketEngine`. The Exercise 3 client communicates with the server over HTTP; the engine contains no JavaFX classes or properties.

## Exercise 3 deployment

Use JDK 25 and Apache Tomcat 9. Copy `guess-market-server/target/guess-market-server.war` into Tomcat's `webapps` directory and start Tomcat on port 8080. Run the batch file in `packaging/ex3-client` with the packaged client JAR and its `lib` directory; the prepared Exercise 3 submission folder contains this layout. The client defaults to `http://localhost:8080/guess-market-server/api/` and can be redirected with the JVM system property `guessmarket.serverUrl`.

Sign in with a name not currently connected. Upload an Exercise 3 XML file to append its events, load account funds, and trade or manage events according to your role. Every client refreshes the shared market approximately once per second. Market and account data are intentionally in server memory only and reset when Tomcat restarts. The optional Exercise 3 chat bonus is not implemented or claimed.

## Build and test

Requirements:

- JDK 25
- Maven 3.9+
- Windows 10 or later for the packaged JavaFX runtime dependencies

```text
mvn clean test
```

The test suite covers the official valid and invalid XML patterns, preservation of the previous market after a failed load, market-maker lifecycle rules, LMSR cash flow, order-book initial shares, price-time matching, partial fills, complementary-order minting, and settlement.

## Exercise 2 run

The submitted archive contains `run.bat`, the application JAR, and every runtime dependency. Extract the complete directory and run `run.bat` from any working directory. The script changes to its own directory before starting the application.

Use **Load XML** to choose an Exercise 2 XML file. Select a user in the Users tab (or from the global **Acting as** selector), choose an event, and use the available controls. Lifecycle controls are enabled only for that event's market maker.

## Implementation decisions

- Events load as inactive. Opening an LMSR event debits the market maker by `C(0,0)`; opening an order-book event debits the configured initial investment and issues `floor(initial / d)` complementary share pairs.
- If the order-book initial investment is not divisible by `d`, the remainder stays as event collateral and is returned to the market maker at settlement.
- Resting-order price and time determine execution priority. A crossing incoming order may consume several resting orders and may remain partially open.
- With mint enabled, complementary BUY orders mint at the minimum remaining quantity. The resting buyer pays the resting quote and the incoming buyer pays the complement to `d`.
- A completed action may make a user balance negative. The UI warns immediately, and the engine blocks all later actions by that user.
- Failed XML loads never replace the last valid in-memory market.
- No Exercise 2 bonus features are claimed.

## Source

[GitHub repository](https://github.com/AmirDayyef/guess-market-ex1-amir-dayyef)
