package com.mortgagemod;

/** Все числа баланса в одном месте. */
public final class Rules {
    public static final int RADIUS = 12;          // участок 25x25 блоков, на всю высоту
    public static final int PRICE = 96;           // цена участка, изумрудов
    public static final int DOWN = 20;            // первоначальный взнос
    public static final int DEFAULT_MONTHS = 12;
    public static final long MONTH = 72000L;      // «месяц» = 3 игровых дня
    public static final long RATE_PERIOD = MONTH * 4; // ключевая ставка меняется раз в 4 месяца
    public static final double FIXED_PREMIUM = 1.5;   // надбавка за фиксированную ставку
    public static final double MIN_KEY = 4, MAX_KEY = 24;
    private Rules() {}
}
