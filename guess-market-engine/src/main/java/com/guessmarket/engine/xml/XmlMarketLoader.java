package com.guessmarket.engine.xml;

import com.guessmarket.api.dto.CommissionType;
import com.guessmarket.api.exception.InvalidMarketFileException;
import com.guessmarket.engine.core.MarketEvent;
import com.guessmarket.engine.core.MarketState;
import com.guessmarket.engine.core.MarketUser;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class XmlMarketLoader {
    public MarketState load(String filePath) {
        Path path = validatePath(filePath);
        XmlMarketFile marketFile = unmarshal(path);
        return validateAndConvert(path, marketFile);
    }

    private Path validatePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new InvalidMarketFileException("The XML file path cannot be empty.");
        }
        final Path path;
        try {
            path = Path.of(filePath.trim());
        } catch (InvalidPathException exception) {
            throw new InvalidMarketFileException("The supplied file path is not valid: " + exception.getReason());
        }
        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xml")) {
            throw new InvalidMarketFileException("The selected file must have an .xml extension.");
        }
        if (!Files.isRegularFile(path)) {
            throw new InvalidMarketFileException("The XML file does not exist or is not a regular file: " + path);
        }
        return path;
    }

    private XmlMarketFile unmarshal(Path path) {
        try (InputStream input = Files.newInputStream(path)) {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            disableExternalXmlEntities(factory);
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                JAXBContext context = JAXBContext.newInstance(XmlMarketFile.class);
                Unmarshaller unmarshaller = context.createUnmarshaller();
                return (XmlMarketFile) unmarshaller.unmarshal(reader);
            } finally {
                reader.close();
            }
        } catch (IOException exception) {
            throw new InvalidMarketFileException("The XML file could not be read: " + exception.getMessage(), exception);
        } catch (JAXBException | XMLStreamException exception) {
            throw new InvalidMarketFileException(
                    "The XML content could not be parsed. Check that it matches the supplied Exercise 2 schema. Details: "
                            + usefulMessage(exception), exception);
        }
    }

    private void disableExternalXmlEntities(XMLInputFactory factory) {
        setPropertyIfSupported(factory, XMLInputFactory.SUPPORT_DTD, false);
        setPropertyIfSupported(factory, "javax.xml.stream.isSupportingExternalEntities", false);
    }

    private void setPropertyIfSupported(XMLInputFactory factory, String property, boolean value) {
        try {
            factory.setProperty(property, value);
        } catch (IllegalArgumentException ignored) {
            // Optional property; the validation below does not rely on it.
        }
    }

    private MarketState validateAndConvert(Path path, XmlMarketFile marketFile) {
        if (marketFile == null || marketFile.getEvents().isEmpty()) {
            throw new InvalidMarketFileException("The XML file must contain at least one event.");
        }
        if (marketFile.getUsers().isEmpty()) {
            throw new InvalidMarketFileException("The XML file must contain at least one user.");
        }

        Set<Integer> eventIds = new LinkedHashSet<>();
        for (int index = 0; index < marketFile.getEvents().size(); index++) {
            XmlMarketFile.XmlEvent event = marketFile.getEvents().get(index);
            if (event.getId() == null) {
                throw new InvalidMarketFileException("Event at position " + (index + 1) + " is missing an ID.");
            }
            if (!eventIds.add(event.getId())) {
                throw new InvalidMarketFileException(
                        "Event ID " + event.getId() + " appears more than once. Event IDs must be unique.");
            }
        }

        Map<String, XmlMarketFile.XmlUser> usersByName = new LinkedHashMap<>();
        Map<Integer, String> marketMakerByEvent = new HashMap<>();
        List<MarketUser> users = new ArrayList<>();
        for (XmlMarketFile.XmlUser xmlUser : marketFile.getUsers()) {
            String userName = requiredText(xmlUser.getName(), "A user has an empty name.");
            String key = userName.toLowerCase(Locale.ROOT);
            if (usersByName.putIfAbsent(key, xmlUser) != null) {
                throw new InvalidMarketFileException("User name " + userName + " appears more than once.");
            }
            if (xmlUser.getInitialCash() == null || xmlUser.getInitialCash() <= 0) {
                throw new InvalidMarketFileException(
                        "User " + userName + " must have an initial cash balance greater than 0.");
            }

            Set<Integer> ownedEvents = new HashSet<>();
            for (Integer eventId : xmlUser.getMarketMakerEventIds()) {
                if (eventId == null) {
                    throw new InvalidMarketFileException("User " + userName + " has a Market Maker entry without an ID.");
                }
                if (!eventIds.contains(eventId)) {
                    throw new InvalidMarketFileException(
                            "User " + userName + " is assigned as Market Maker for event " + eventId
                                    + ", but that event does not exist.");
                }
                if (!ownedEvents.add(eventId)) {
                    throw new InvalidMarketFileException(
                            "User " + userName + " lists event " + eventId + " more than once as Market Maker.");
                }
                String previous = marketMakerByEvent.putIfAbsent(eventId, userName);
                if (previous != null) {
                    throw new InvalidMarketFileException(
                            "Event " + eventId + " has more than one Market Maker: " + previous + " and " + userName + ".");
                }
            }
            users.add(new MarketUser(userName, xmlUser.getInitialCash(), ownedEvents));
        }

        for (Integer eventId : eventIds) {
            if (!marketMakerByEvent.containsKey(eventId)) {
                throw new InvalidMarketFileException("Event " + eventId + " must have exactly one Market Maker.");
            }
        }

        List<MarketEvent> events = new ArrayList<>();
        for (XmlMarketFile.XmlEvent xmlEvent : marketFile.getEvents()) {
            events.add(convertEvent(xmlEvent, marketMakerByEvent.get(xmlEvent.getId())));
        }
        return new MarketState(path.toAbsolutePath().normalize().toString(), events, users);
    }

    private MarketEvent convertEvent(XmlMarketFile.XmlEvent xmlEvent, String marketMakerName) {
        int id = xmlEvent.getId();
        String name = requiredText(xmlEvent.getName(), "Event " + id + " has an empty name.");
        String description = requiredText(
                xmlEvent.getDescription(), "Event " + id + " has an empty description.");
        if (xmlEvent.getCommission() == null || xmlEvent.getCommission().getPercentage() == null) {
            throw new InvalidMarketFileException("Event " + id + " is missing its commission definition.");
        }
        int commission = xmlEvent.getCommission().getPercentage();
        if (commission < 0 || commission > 90) {
            throw new InvalidMarketFileException(
                    "Event " + id + " has commission " + commission + "%. Commission must be between 0% and 90%.");
        }
        CommissionType commissionType = parseCommissionType(xmlEvent.getCommission().getType(), id);

        List<String> options = xmlEvent.getOptions().stream()
                .map(value -> value == null ? "" : value.trim())
                .toList();
        if (options.size() != 2) {
            throw new InvalidMarketFileException("Event " + id + " must contain exactly two options.");
        }
        if (options.stream().anyMatch(String::isBlank)) {
            throw new InvalidMarketFileException("Event " + id + " contains an empty option name.");
        }
        if (options.get(0).equalsIgnoreCase(options.get(1))) {
            throw new InvalidMarketFileException("Event " + id + " must contain two different option names.");
        }

        boolean hasLmsr = xmlEvent.getLmsr() != null;
        boolean hasOrderBook = xmlEvent.getOrderBook() != null;
        if (hasLmsr == hasOrderBook) {
            throw new InvalidMarketFileException(
                    "Event " + id + " must define exactly one trading method: LMSR or Order Book.");
        }
        if (hasLmsr) {
            Integer liquidity = xmlEvent.getLmsr().getLiquidity();
            if (liquidity == null || liquidity <= 0) {
                throw new InvalidMarketFileException("Event " + id + " must have a positive LMSR liquidity value (b).");
            }
            return MarketEvent.lmsr(
                    id, name, description, commission, commissionType,
                    liquidity, marketMakerName, options);
        }

        XmlMarketFile.XmlOrderBook orderBook = xmlEvent.getOrderBook();
        if (orderBook.getAllowMint() == null) {
            throw new InvalidMarketFileException("Event " + id + " is missing the allow-mint value.");
        }
        if (orderBook.getInitialInvestment() == null || orderBook.getInitialInvestment() < 0) {
            throw new InvalidMarketFileException(
                    "Event " + id + " must have an Order Book initial investment of 0 or more.");
        }
        if (orderBook.getBaseValue() == null || orderBook.getBaseValue() <= 0) {
            throw new InvalidMarketFileException("Event " + id + " must have a positive Order Book base value (d).");
        }
        return MarketEvent.orderBook(
                id, name, description, commission, commissionType,
                orderBook.getAllowMint(), orderBook.getInitialInvestment(), orderBook.getBaseValue(),
                marketMakerName, options);
    }

    private CommissionType parseCommissionType(String value, int eventId) {
        if (value == null) {
            throw new InvalidMarketFileException("Event " + eventId + " is missing its commission type.");
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "on-purchase" -> CommissionType.ON_PURCHASE;
            case "on-close" -> CommissionType.ON_CLOSE;
            default -> throw new InvalidMarketFileException(
                    "Event " + eventId + " has an unsupported commission type: " + value.trim());
        };
    }

    private String requiredText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidMarketFileException(message);
        }
        return value.trim();
    }

    private String usefulMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
