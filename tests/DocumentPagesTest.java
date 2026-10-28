package io.github.miclean.module;
public final class DocumentPagesTest {
    private static void check(boolean value){if(!value)throw new AssertionError();}
    private static void roundTrip(String document){DocumentPages pages=new DocumentPages(document);StringBuilder rebuilt=new StringBuilder();for(int i=0;i<pages.size();i++){String p=pages.page(i);check(!p.isEmpty()&&p.length()<=DocumentPages.PAGE_CHARS);check(!Character.isLowSurrogate(p.charAt(0)));check(!Character.isHighSurrogate(p.charAt(p.length()-1)));rebuilt.append(p);}check(document.contentEquals(rebuilt));}
    public static void main(String[] args){
        roundTrip("");roundTrip("{}\r\n");roundTrip(new String(new char[2047]).replace('\0','a')+"🙂后续");
        roundTrip(new String(new char[3_000_000]).replace('\0','a'));roundTrip("规则\r\n🙂\n".repeat(15000));roundTrip("\n".repeat(9000));
        String huge="{data:'"+"a".repeat(6_000_000)+"'}";long before=System.nanoTime();DocumentPages pages=new DocumentPages(huge);long millis=(System.nanoTime()-before)/1_000_000;
        check(pages.size()>2900);check(pages.page(pages.size()-1).endsWith("'}"));check(millis<3000);
        System.out.println("PASS lossless bounded JSON5 pages: empty, CRLF, surrogate boundary, dense lines and 6MB single-line index in "+millis+"ms");
    }
}
