package com.guessmarket.engine.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "Guess-Market")
@XmlAccessorType(XmlAccessType.FIELD)
public final class XmlMarketFile {
    @XmlElement(name = "GM-events", required = true)
    private XmlEvents events;

    @XmlElement(name = "GM-users", required = true)
    private XmlUsers users;

    public XmlMarketFile() {
    }

    public List<XmlEvent> getEvents() {
        return events == null ? List.of() : events.events;
    }

    public List<XmlUser> getUsers() {
        return users == null ? List.of() : users.users;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlEvents {
        @XmlElement(name = "GM-event", required = true)
        private List<XmlEvent> events = new ArrayList<>();

        public XmlEvents() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlEvent {
        @XmlAttribute(name = "name", required = true)
        private String name;

        @XmlElement(name = "id", required = true)
        private Integer id;

        @XmlElement(name = "description", required = true)
        private String description;

        @XmlElements({
                @XmlElement(name = "comision", type = XmlCommission.class),
                @XmlElement(name = "commission", type = XmlCommission.class)
        })
        private XmlCommission commission;

        @XmlElement(name = "GM-options", required = true)
        private XmlOptions options;

        @XmlElement(name = "GM-method", required = true)
        private XmlMethod method;

        public XmlEvent() {
        }

        public String getName() { return name; }
        public Integer getId() { return id; }
        public String getDescription() { return description; }
        public XmlCommission getCommission() { return commission; }
        public List<String> getOptions() { return options == null ? List.of() : options.options; }
        public XmlLmsr getLmsr() { return method == null ? null : method.lmsr; }
        public XmlOrderBook getOrderBook() { return method == null ? null : method.orderBook; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlCommission {
        @XmlAttribute(name = "type", required = true)
        private String type;

        @jakarta.xml.bind.annotation.XmlValue
        private Integer percentage;

        public XmlCommission() {
        }

        public String getType() { return type; }
        public Integer getPercentage() { return percentage; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlOptions {
        @XmlElement(name = "GM-option", required = true)
        private List<String> options = new ArrayList<>();

        public XmlOptions() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlMethod {
        @XmlElement(name = "GM-LMSR")
        private XmlLmsr lmsr;

        @XmlElement(name = "GM-order-book")
        private XmlOrderBook orderBook;

        public XmlMethod() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlLmsr {
        @XmlElement(name = "b", required = true)
        private Integer liquidity;

        public XmlLmsr() {
        }

        public Integer getLiquidity() { return liquidity; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlOrderBook {
        @XmlAttribute(name = "allow-mint", required = true)
        private Boolean allowMint;

        @XmlAttribute(name = "initial", required = true)
        private Integer initialInvestment;

        @XmlAttribute(name = "d", required = true)
        private Integer baseValue;

        public XmlOrderBook() {
        }

        public Boolean getAllowMint() { return allowMint; }
        public Integer getInitialInvestment() { return initialInvestment; }
        public Integer getBaseValue() { return baseValue; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlUsers {
        @XmlElement(name = "GM-user", required = true)
        private List<XmlUser> users = new ArrayList<>();

        public XmlUsers() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlUser {
        @XmlAttribute(name = "name", required = true)
        private String name;

        @XmlElement(name = "initial-cash", required = true)
        private Integer initialCash;

        @XmlElement(name = "GM-market-maker")
        private XmlMarketMaker marketMaker;

        public XmlUser() {
        }

        public String getName() { return name; }
        public Integer getInitialCash() { return initialCash; }
        public List<Integer> getMarketMakerEventIds() {
            if (marketMaker == null) {
                return List.of();
            }
            return marketMaker.events.stream().map(XmlEventReference::getId).toList();
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlMarketMaker {
        @XmlElement(name = "event", required = true)
        private List<XmlEventReference> events = new ArrayList<>();

        public XmlMarketMaker() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlEventReference {
        @XmlAttribute(name = "id", required = true)
        private Integer id;

        public XmlEventReference() {
        }

        public Integer getId() { return id; }
    }
}
