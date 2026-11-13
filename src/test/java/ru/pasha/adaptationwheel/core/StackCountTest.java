package ru.pasha.adaptationwheel.core;

public final class StackCountTest {
    public static void run(){
        check(StackCount.format(1).equals("1")&&StackCount.format(64).equals("64")&&StackCount.format(999).equals("999"),"Small stacks must show the exact count");
        check(StackCount.format(1000).equals("1k"),"1000 -> 1k");
        check(StackCount.format(1250).equals("1.2k"),"1250 -> 1.2k");
        check(StackCount.format(1999).equals("1.9k"),"Labels round down, never claim more than the slot holds");
        check(StackCount.format(12_345).equals("12k")&&StackCount.format(999_999).equals("999k"),"Tens and hundreds of thousands");
        check(StackCount.format(1_000_000).equals("1M")&&StackCount.format(2_500_000).equals("2.5M")&&StackCount.format(345_000_000).equals("345M"),"Millions");
        check(StackCount.format(StackCount.LIMIT).equals("1B"),"The limit itself");
        for(int count:new int[]{1000,1999,10_000,99_999,999_999,1_099_999,9_999_999,99_999_999,999_999_999,StackCount.LIMIT,Integer.MAX_VALUE})
            check(StackCount.format(count).length()<=4,"Label wider than a slot: "+StackCount.format(count));
        check(StackCount.grouped(12_345_678).equals("12 345 678")&&StackCount.grouped(1000).equals("1 000")&&StackCount.grouped(999).equals("999"),"Tooltip grouping");
        check((long)StackCount.LIMIT*2<=Integer.MAX_VALUE,"Two full stacks must add up without int overflow");
        System.out.println("StackCountTest: slot labels, rounding, width, tooltip grouping and overflow headroom passed.");
    }
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
