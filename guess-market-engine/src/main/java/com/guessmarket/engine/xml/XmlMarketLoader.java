package com.guessmarket.engine.xml;

import com.guessmarket.api.dto.CommissionType;
import com.guessmarket.api.exception.InvalidMarketFileException;
import com.guessmarket.engine.core.MarketEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class XmlMarketLoader {
    public List<MarketEvent> load(String filePath) {
        Path path = validatePath(filePath);
        XmlMarketFile marketFile = unmarshal(path);
        return validateAndConvert(marketFile);
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
                    "The XML content could not be parsed. Check that it matches the supplied Exercise 1 schema. Details: "
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
            // The current XML implementation does not expose this optional property.
        }
    }

    private List<MarketEvent> validateAndConvert(XmlMarketFile marketFile) {
        if (marketFile == null || marketFile.getEvents().isEmpty()) {
            throw new InvalidMarketFileException("The XML file must contain at least one event.");
        }

        Set<Integer> eventIds = new HashSet<>();
        List<MarketEvent> events = new ArrayList<>();
        int position = 0;
        for (XmlMarketFile.XmlEvent xmlEvent : marketFile.getEvents()) {
            position++;
            if (xmlEvent.getId() == null) {
                throw invalidEvent(position, "is missing an ID");
            }
            int id = xmlEvent.getId();
            if (!eventIds.add(id)) {
                throw new InvalidMarketFileException("Event ID " + id + " appears more than once. Event IDs must be unique.");
            }

            String name = requiredText(xmlEvent.getName(), "name", id);
            String description = requiredText(xmlEvent.getDescription(), "description", id);
            if (xmlEvent.getCommission() == null || xmlEvent.getCommission().getPercentage() == null) {
                throw new InvalidMarketFileException("Event " + id + " is missing its commission definition.");
            }
            int commission = xmlEvent.getCommission().getPercentage();
            if (commission < 0 || commission > 90) {
                throw new InvalidMarketFileException(
                        "Event " + id + " has commission " + commission + "%. Commission must be between 0% and 90%.");
            }
            CommissionType commissionType = parseCommissionType(xmlEvent.getCommission().getType(), id);

            List<String> optionNames = xmlEvent.getOptions().stream().map(String::trim).toList();
            if (optionNames.size() != 2) {
                throw new InvalidMarketFileException("Event " + id + " must contain exactly two options.");
            }
            if (optionNames.stream().anyMatch(String::isBlank)) {
                throw new InvalidMarketFileException("Event " + id + " contains an empty option name.");
            }
            if (optionNames.get(0).equalsIgnoreCase(optionNames.get(1))) {
                throw new InvalidMarketFileException("Event " + id + " must contain two different option names.");
            }

            Integer liquidity = xmlEvent.getLiquidity();
            if (liquidity == null || liquidity <= 0) {
                throw new InvalidMarketFileException("Event " + id + " must have a positive LMSR liquidity value (b).");
            }
            events.add(new MarketEvent(
                    id, name, description, commission, commissionType, liquidity, optionNames));
        }
        return List.copyOf(events);
    }

    private CommissionType parseCommissionType(String value, int eventId) {
        if (value == null) {
            throw new InvalidMarketFileException("Event " + eventId + " is missing its commission type.");
        }
        return switch (value.trim()) {
            case "on-purchase" -> CommissionType.ON_PURCHASE;
            case "on-close" -> CommissionType.ON_CLOSE;
            default -> throw new InvalidMarketFileException(
                    "Event " + eventId + " has an unsupported commission type: " + value.trim());
        };
    }

    private String requiredText(String value, String field, int eventId) {
        if (value == null || value.trim().isEmpty()) {
            throw new InvalidMarketFileException("Event " + eventId + " has an empty " + field + ".");
        }
        return value.trim();
    }

    private InvalidMarketFileException invalidEvent(int position, String problem) {
        return new InvalidMarketFileException("Event at position " + position + " " + problem + ".");
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
