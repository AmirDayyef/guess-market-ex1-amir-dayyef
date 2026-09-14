# Guess Market - Exercise 2

A Java 25 and JavaFX implementation of a two-option prediction market. The application supports both LMSR and central-limit-order-book trading, multiple users, market-maker lifecycle control, commissions, settlement, and XML validation.

## Participants

- Amir Dayyef - ID `323092023` - [amirdayyef@gmail.com](mailto:amirdayyef@gmail.com)
- Nour Guty - ID `322235516` - [nourguty581@gmail.com](mailto:nourguty581@gmail.com)

## Project structure

- `guess-market-api` - the UI-independent engine interface, immutable DTO records, enums, and public exceptions.
- `guess-market-engine` - XML loading and validation, accounts, LMSR pricing, order matching, peer minting, commissions, and settlement.
- `guess-market-javafx` - the JavaFX application, background XML-loading task, event and user screens, filters, tables, and trading controls.
- `guess-market-console` - the preserved Exercise 1 console module. It is not part of the Exercise 2 Maven reactor or submission.

The JavaFX module communicates with the engine only through `GuessMarketEngine`. The engine contains no JavaFX classes or properties.

## Build and test

Requirements:

- JDK 25
- Maven 3.9+
- Windows 10 or later for the packaged JavaFX runtime dependencies

```text
mvn clean test
```

The test suite covers the official valid and invalid XML patterns, preservation of the previous market after a failed load, market-maker lifecycle rules, LMSR cash flow, order-book initial shares, price-time matching, partial fills, complementary-order minting, and settlement.

## Run

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
