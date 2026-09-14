package com.guessmarket.ui;

import javafx.scene.control.TableCell;

import java.util.Locale;

final class MoneyTableCell<T> extends TableCell<T, Number> {
    @Override
    protected void updateItem(Number value, boolean empty) {
        super.updateItem(value, empty);
        setText(empty || value == null ? null : String.format(Locale.US, "$%,.2f", value.doubleValue()));
    }
}
