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

    public XmlMarketFile() {
    }

    public List<XmlEvent> getEvents() {
        return events == null ? List.of() : events.events;
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

        // "comision" is intentionally spelled exactly as it is in the supplied XSD.
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

        public String getName() {
            return name;
        }

        public Integer getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public XmlCommission getCommission() {
            return commission;
        }

        public List<String> getOptions() {
            return options == null ? List.of() : options.options;
        }

        public Integer getLiquidity() {
            return method == null || method.lmsr == null ? null : method.lmsr.liquidity;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlCommission {
        @XmlAttribute(name = "type", required = true)
        private String type;

        @jakarta.xml.bind.annotation.XmlValue
        private Integer percentage;

        public XmlCommission() {
        }

        public String getType() {
            return type;
        }

        public Integer getPercentage() {
            return percentage;
        }
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
        @XmlElement(name = "GM-LMSR", required = true)
        private XmlLmsr lmsr;

        public XmlMethod() {
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class XmlLmsr {
        @XmlElement(name = "b", required = true)
        private Integer liquidity;

        public XmlLmsr() {
        }
    }
}
