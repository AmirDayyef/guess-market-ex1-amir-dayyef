# Guess Market – Exercise 1

A Java 25 console implementation of a binary LMSR prediction market.

## Participants

- Amir Dayyef — ID `323092023` — [amirdayyef@gmail.com](mailto:amirdayyef@gmail.com)
- Nour Guty — ID `322235516` — [nourguty581@gmail.com](mailto:nourguty581@gmail.com)

## Project structure

- `guess-market-api` – the engine interface, immutable DTO records, enums, and public exceptions.
- `guess-market-engine` – XML loading and validation, LMSR pricing, trading, settlement, commissions, and complete-state persistence.
- `guess-market-console` – the English console UI and application entry point.

The console communicates with the engine only through `GuessMarketEngine`. Each module is packaged as its own JAR; the project does not build a fat JAR.

## Requirements and build

- JDK 25
- Maven 3.9+

```text
mvn clean verify
```

The engine test suite covers valid and invalid XML loads, state preservation after a failed load, LMSR pricing and trade costs, both commission modes, event settlement, input validation, and the state save/load bonus.

## Run from Maven output

After `mvn clean package`, launch with the three module JARs and JAXB runtime dependencies on the classpath. The course submission archive includes a ready-to-use `run.bat` and every required runtime JAR.

## Implemented bonus

Menu commands 7 and 8 save and restore the complete market state in a `.gmstate` file. A state file is fully read before it replaces the current market, so a damaged or incompatible file leaves the running state unchanged.

## Important XML detail

The official Exercise 1 XML uses the tag `<comision>`. The loader accepts that supplied spelling and also supports `<commission>` for compatibility.
