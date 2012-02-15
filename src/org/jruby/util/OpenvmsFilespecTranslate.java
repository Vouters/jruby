/*
 ***** BEGIN LICENSE BLOCK *****
 * Version: CPL 1.0/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Common Public
 * License Version 1.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.eclipse.org/legal/cpl-v10.html
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Copyright (C) 2012 Philippe Vouters <Philippe.Vouters@laposte.net>
 * 
 * Alternatively, the contents of this file may be used under the terms of
 * either of the GNU General Public License Version 2 or later (the "GPL"),
 * or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the CPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the CPL, the GPL or the LGPL.
 ***** END LICENSE BLOCK *****/
package org.jruby.util;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.IOException;

public class OpenvmsFilespecTranslate {
   //
   // For ODS-5 special characters and valid character set, refer to :
   // http://h71000.www7.hp.com/doc/73final/6536/6536pro_002.html#ods5names_hd
   // In fact this above HP document is not fully correct: the colon character
   // is indeed valid in a directory or filename specifications. The colon
   // character enables HP OpenVMS GNV bash file syntaxes like 'c:/foo old.txt'
   // or foo:blah.txt
   //
   // This code handles full ODS-2 and ODS-5 file syntaxes. This includes
   // caret escaped hexadecimal coded characters like ^20 meaning a space
   // character or caret escaped Unicode characters featured by the syntax
   // ^Uwxyz with wxyz being hexadecimal coded. For example: ^Uc2bc^.foo.save
   // converts to <vulgar fraction one quarter character>.foo.save
   // Refer to http://www.utf8-chartable.de/unicode-utf8-table.pl for valid
   // Unicode characters.
   //
   // Control characters either hexadecimal or Unicode expressed in the range
   // 0x00 to 0x1F are handled as invalid by this code in VMS filespec. This
   // matches the VMS valid file specifications in the above HP document. The
   // result is that this full code returns null indicating an invalid VMS file
   // specification.
   //
   public static String validEscapedCharacters = "+,;\\[\\]%\\:\\^& "+
                                                 "012345789ABCDEFU._";
   public static String invalidOds5Characters = "\"\\*\\\\<>/\\?\\|";

   private static class StringAndIntRecord{
       private String vmstoUnixFilespec;
       private String vmsFilespec;
       private int startStringIndex;

       private static final char[] kDigits = { '0', '1', '2', '3', '4', '5',
                                          '6', '7', '8', '9', 'A',
                                          'B', 'C', 'D', 'E', 'F' };

       private int CheckValidEscapedCharacter(){
          //
          // Parse the input string to check whether all circumflex characters
          // are immediately followed by a valid escaped character.
          // Except the dot, check whether an escaped character is not found
          // without a preceeding circumflex character.
          //
          for (int i = startStringIndex; i < vmsFilespec.length(); i++){
               if (vmsFilespec.charAt(i) == '^' && i < vmsFilespec.length()-1){
                   int escapeCharIndex =
                       validEscapedCharacters.indexOf(vmsFilespec.charAt(i+1));
                   if (escapeCharIndex == -1)
                       return -1;
                   if (escapeCharIndex < 
                               validEscapedCharacters.indexOf('0') ||
                        escapeCharIndex > 
                               validEscapedCharacters.indexOf('U')){
                       i++;
                       continue;                  
                   }
                   else if (escapeCharIndex >=
                                      validEscapedCharacters.indexOf('0') && 
                            escapeCharIndex < 
                                      validEscapedCharacters.indexOf('U') &&
                            i < vmsFilespec.length() - 2){
                       for (int j= 0; j < 2; j++){
                           if (vmsFilespec.charAt(i+1+j) >= 'a' &&
                               vmsFilespec.charAt(i+1+j) <= 'f')
                               vmsFilespec = vmsFilespec.substring(0,i+1+j) +
                                             Character.toUpperCase(
                                                  vmsFilespec.charAt(i+1+j)) +
                                                  vmsFilespec.substring(i+2+j,
                                                        vmsFilespec.length());
                       }
                       for (int j= 0; j < 2; j++){
                            if (!Character.isDigit(vmsFilespec.charAt(i+1+j))&&
                                (vmsFilespec.charAt(i+1+j) < 'A' ||
                                 vmsFilespec.charAt(i+1+j) > 'F'))
                            return -1;
                       }
                       i += 2;
                       continue;
                   }
                   else if (escapeCharIndex == 
                                  validEscapedCharacters.indexOf('U') &&
                            i < vmsFilespec.length() - 5) {
                       for (int j = 0; j < 4 ; j++){
                            if (vmsFilespec.charAt(i+2+j) >= 'a' &&
                                vmsFilespec.charAt(i+2+j) <= 'f')
                               vmsFilespec = vmsFilespec.substring(0,i+2+j) +
                                              Character.toUpperCase(
                                                  vmsFilespec.charAt(i+2+j)) +
                                              vmsFilespec.substring(i+3+j,
                                                        vmsFilespec.length());
                       }
                       for (int j= 0; j < 4; j++){
                            if (!Character.isDigit(vmsFilespec.charAt(i+2+j))&&
                                (vmsFilespec.charAt(i+2+j) < 'A' ||
                                 vmsFilespec.charAt(i+2+j) > 'F'))
                            return -1;
                       }
                       i += 5;
                       continue;
                   }
                   else
                       return -1;
               }
               int escapeCharIndex =
                       validEscapedCharacters.indexOf(vmsFilespec.charAt(i));
               if (escapeCharIndex != -1 &&
                   escapeCharIndex < validEscapedCharacters.indexOf('0'))
                   return -1;
          }
          return 0;
       }

       //
       // the two hexToBytes methods are copied from:
       // http://www.java2s.com/Code/Java/Development-Class/ConverthexToBytes.htm
       //
       public static byte[] hexToBytes(char[] hex) {
           int length = hex.length / 2;
           byte[] raw = new byte[length];
           for (int i = 0; i < length; i++) {
                int high = Character.digit(hex[i * 2], 16);
                int low = Character.digit(hex[i * 2 + 1], 16);
                int value = (high << 4) | low;
                if (value > 127)
                    value -= 256;
                raw[i] = (byte) value;
           }
           return raw;
       }

       public static byte[] hexToBytes(String hex) {
           return hexToBytes(hex.toCharArray());
       }

       public String getVmstoUnixFilespec(){
           return vmstoUnixFilespec;
       }

       public StringAndIntRecord(String vmsFilespec,
                                 int startStringIndex){
            this.startStringIndex = startStringIndex;
            this.vmsFilespec = vmsFilespec;
       }

       public void turnAllCircumflex(boolean addSlash){
          int posDot;

          vmstoUnixFilespec = "";
          if (vmsFilespec.length() == 0)
              return;

          if (CheckValidEscapedCharacter() == -1){
              vmstoUnixFilespec = null;
              return;
          }
          //
          // Process all dots except if it starts the string and if not
          // preceeded by a cicumflex character for a directory operation
          // (addSlash == true). For a file operation (addSlash == false),
          // treat the dot as invalid if not preceeded by a circumflex.
          // If preceeded by a circumflex, this is processed later in this
          // routine in the final while loop.
          //
          while ((posDot = vmsFilespec.indexOf('.',startStringIndex)) != -1){
                if (startStringIndex < posDot &&
                    vmsFilespec.charAt(posDot-1) != '^'){
                    vmstoUnixFilespec += vmsFilespec.substring(startStringIndex,
                                                               posDot);
                    if (addSlash)
                        vmstoUnixFilespec += "/";
                    else
                        vmstoUnixFilespec += ".";
                }
                else if (posDot != startStringIndex &&
                         vmsFilespec.charAt(posDot-1) == '^')                    
                    vmstoUnixFilespec += vmsFilespec.substring(startStringIndex,
                                                                 posDot+1);
                else if (posDot == startStringIndex  && !addSlash){
                    vmstoUnixFilespec += ".";
                }
                startStringIndex = posDot+1;
          }
          //
          // No more dots to process, add in the remaining of the input string
          //
          if (startStringIndex < vmsFilespec.length())
              vmstoUnixFilespec += vmsFilespec.substring(startStringIndex,
                                            vmsFilespec.length());
         //
         // Add in final slash if requested.
         //
         if (addSlash && vmstoUnixFilespec.length() != 0)
              vmstoUnixFilespec += "/";

         //
         // Process VMS file specification escape characters.
         //
         posDot = 0;
         while ((posDot = vmstoUnixFilespec.indexOf('^',posDot)) != -1){
                if (validEscapedCharacters.indexOf(
                                    vmstoUnixFilespec.charAt(posDot+1)) != -1){
                    int escapeCharIndex =
                        validEscapedCharacters.indexOf(vmstoUnixFilespec.charAt(posDot+1));
                    if (escapeCharIndex <
                                      validEscapedCharacters.indexOf('0') ||
                        escapeCharIndex >
                                      validEscapedCharacters.indexOf('U')){ 
                        if (validEscapedCharacters.charAt(escapeCharIndex) ==
                                             '_') {
                            vmstoUnixFilespec =
                                        vmstoUnixFilespec.substring(0,posDot) +
                                           " " +
                                        vmstoUnixFilespec.substring(posDot+2,
                                                   vmstoUnixFilespec.length());
                        }
                        else {
                            vmstoUnixFilespec = 
                                        vmstoUnixFilespec.substring(0,posDot) +
                                           vmstoUnixFilespec.charAt(posDot+1) +
                                           vmstoUnixFilespec.substring(posDot+2,
                                                    vmstoUnixFilespec.length());
                       }
                    }
                    else if (escapeCharIndex >=
                                      validEscapedCharacters.indexOf('0') &&
                             escapeCharIndex <
                                 validEscapedCharacters.indexOf('U')){
                       byte[] hexChar = hexToBytes(
                                       vmstoUnixFilespec.substring(posDot+1,
                                                                  posDot+3));
                       // Do not accept control characters in the
                       // range 0x00 to 0x1F                         
                       if (hexChar[0] < 32 ){
                           vmstoUnixFilespec = null;
                           return;
                       }

                       vmstoUnixFilespec =
                                        vmstoUnixFilespec.substring(0,posDot) +
                                         (char)hexChar[0] +
                                         vmstoUnixFilespec.substring(posDot+3,
                                                   vmstoUnixFilespec.length());
                    }
                    else if (escapeCharIndex ==
                                 validEscapedCharacters.indexOf('U')){
                          byte [] hexTwoBytes = hexToBytes(
                                       vmstoUnixFilespec.substring(posDot+2,
                                                                   posDot+6));
                          // Do not accept control characters in the
                          // range 0x00 to 0x1F
                          if (hexTwoBytes[0] == 0 && hexTwoBytes[1] < 32 ){
                              vmstoUnixFilespec = null;
                              return;
                          }

                          String UnicodeChar = new String(hexTwoBytes);
                          vmstoUnixFilespec =
                                        vmstoUnixFilespec.substring(0,posDot) +
                                        UnicodeChar +
                                        vmstoUnixFilespec.substring(posDot+6,
                                                  vmstoUnixFilespec.length());
                   }    
                }
                posDot++;
         }

         //
         // process remaining "-/" in directory paths and turn them to "../".
         // This code sequence can eventually be simplified with using the
         // String.replace method provided this Java method accepts Strings as
         // input and output arguments.
         if (addSlash){
            posDot = 0;
            while ((posDot = vmstoUnixFilespec.indexOf("-/",posDot)) != -1){
                  vmstoUnixFilespec = vmstoUnixFilespec.substring(0,posDot) +
                                       "../" +
                                      vmstoUnixFilespec.substring(posDot+2,
                                                   vmstoUnixFilespec.length());
                  posDot += 3;
            }
         }
       }
   }

   //
   // Check for invalid file version.
   // VMS valid file version is in the range 1 to 32768.
   //
   private static boolean checkVersionNumber(String version){
      int versionNumber = Integer.parseInt(version);
      if (versionNumber > 32767)
          return false;
      return true;
   }

   //
   // This vmsFilespecToUnix method is the main method to be called when
   // converting a filename to a Unix style filename. It returns either
   // null if the input filename is not a valid VMS filespec or the full
   // Unix filespec equivalent. To not interfere with pure Windows or Unix
   // filespecs, this code returns null as soon as the input filename does
   // not contain the pairs '[' and ']' or '<' and '>' which are specific to
   // OpenVMS acting as directory delimiters.
   //

   public static String vmsFilespecToUnix (String vmsFilespec) {
       //
       // Set up all Regular Expressions which will be used to correctly
       // parse the VMS file name.
       //
       String diskname = "^([^\\["+invalidOds5Characters+"\\]]+:)?";
       String directory = "((\\[|<)" +
                          "([^"+invalidOds5Characters+"]){0,}"+
                          "(\\]|>))?";
       String filename = "([^"+invalidOds5Characters+"]+)";
       String extension = "(\\.([^"+invalidOds5Characters+"]+)?)";
       String version = "(;(\\d+)?)?";

       Pattern regex = Pattern.compile(diskname +
				 directory +
                                 filename + "{0,1}" +
                                 extension +
                                 version);
       //
       // Initialize the Regex MAX_MATCH constant which will be
       // used in the for loop later in this code.
       //
       int MAX_MATCH = 10;
       Matcher fit=regex.matcher(vmsFilespec);
       if (!fit.matches()){
           //
           // No extension and version specified is quite valid
           // for a VMS filespec. Handle this case, adjusting the
           // MAX_MATCH constant.
           //
           regex = Pattern.compile(diskname +
                                   directory +
                                   filename);
           fit=regex.matcher(vmsFilespec);
           if (!fit.matches()) {
               return null;
           } else {
               MAX_MATCH = 7;
           }
       }
       //
       // To check how much valid are the above Regex expressions, you may
       // java activate this code with -Ddebug=true or under JRuby:
       // export JRUBY_OPTS="-Ddebug=true". To best understand how this code
       // works, you may download the simple Java code using it from
       // http://vouters.dyndns.org/zip/TestFileSyntax.zip and play injecting
       // various VMS filenames along with -Ddebug=true as an argument to the
       // Java command. For more information, refer to two public Web documents:
       // http://vouters.dyndns.org/tima/OpenVMS-Java-Unix-CRTL_API_decc$translate_name-Java_solution.html
       // http://vouters.dyndns.org/tima/All-OS-Java-Java_code_dealing_with_OS_specific_file_syntaxes.html
       //
       boolean debug = Boolean.valueOf(System.getProperty("debug","false"));
       if (debug) {
          for (int i=0; i<MAX_MATCH;i++)
               System.out.println("fit.group("+i+") = "+fit.group(i));
       }
       char closingDir = 0;

       //
       // fit.group(3) and fit.group(5) hold the directory delimiters.
       // 
       if (fit.group(3) == null || fit.group(5) == null) {
           return null;
       }
       if (fit.group(3).compareTo("[") == 0 && fit.group(5).compareTo("]") != 0) {
           return null;
       }
       if (fit.group(3).compareTo("<") == 0 && fit.group(5).compareTo(">") != 0) {
           return null;
       }
       closingDir = fit.group(5).charAt(0);
       //
       // This test is to not return . for an input filemane .
       // It is likely this test can be suppressed as now this
       // code checks for ('[',']','<','>'. Acceptance tests must be
       // performed if suppressed. Two reasons for suppressing it:
       // performance and code simplifications.
       //
       if (MAX_MATCH == 10 && fit.group(7) != null &&
           fit.group(7).matches(".")) {
           return null;
       }

       //
       // Base the return filespec onto the current directory. In fact, a VMS
       // user may specificy disk:[dir.subdir]file.ext in which case the Unix
       // equivalent is /disk/dir/subdir/file.ext or [dir.subdir]file.ext in
       // which the Unix equivalent is /<current disk>/dir/subdir/file.ext or
       // [.dir.subdir]file.ext in which case the Unix equivalent is
       // `pwd`/dir/subdir/file.ext or disk:[.dir.subdir]file.ext in which case
       // the Unix equivalent is /disk/<all `pwd` but disk>/dir/subdir/file.ext
       //
       String currentDir;
       try {
           currentDir = new java.io.File( "." ).getCanonicalPath();
       } catch (IOException e) {
           return null;
       }
       int posDot;
       int startStringIndex = 1;
       boolean processMatch2 = true;
       String vmstoUnixFilespec = new String(currentDir); 
      
       for (int i = 1; i < MAX_MATCH; i++){
           switch (i) {
              case 1:
                   //
                   // Deal with disk: if any.
                   //
                   if (fit.group(1) != null){
                       int posSlash = vmstoUnixFilespec.indexOf('/',1);
                       if (posSlash == -1) {
                           posSlash = vmstoUnixFilespec.length();
                       } else {
                           posSlash++;
                       }
                       StringAndIntRecord record = 
                                 new StringAndIntRecord(
                                        fit.group(1).substring(
                                        0, fit.group(1).indexOf(':')), 0);
                       record.turnAllCircumflex(true);
                       String returnedString = record.getVmstoUnixFilespec();
                       if (returnedString == null) {
                           return null;
                       }
                       vmstoUnixFilespec = "/"+ returnedString +
                                           vmstoUnixFilespec.substring(
                                                 posSlash,
                                                 vmstoUnixFilespec.length());
                       }
                       //
                       // Append '/' if not already
                       //
                       if (vmstoUnixFilespec.length() != 0 &&
                           vmstoUnixFilespec.charAt(
                               vmstoUnixFilespec.length()- 1) != '/') {
                           vmstoUnixFilespec += "/";
                        }
                        break;
              case 2:
                   //
                   // Deal with everyting which is a dirctory specification
                   // if any (i.e: everyting between '[' or '<' and ']' or '>')
                   //
                   if (fit.group(2) != null && processMatch2 &&
                       startStringIndex < fit.group(2).length()) {
                       StringAndIntRecord record = new StringAndIntRecord(
                                    fit.group(2).substring(
                                             startStringIndex,
                                             fit.group(2).indexOf(closingDir)),
                                    0);
                       record.turnAllCircumflex(true);
                       String returnedString = record.getVmstoUnixFilespec();
                       if (returnedString == null)
                           return null;
                       //
                       // Readjust vmstoUnixFilespec if [dir.subdir]
                       //
                       if (fit.group(2).charAt(startStringIndex) != '.' &&
                           fit.group(2).charAt(startStringIndex) != closingDir) {
                           vmstoUnixFilespec = "/" +
                                               vmstoUnixFilespec.substring(1,
                                                    vmstoUnixFilespec.indexOf('/',1)+1);
                       }
                       vmstoUnixFilespec += returnedString;
                   }
                   break;
              case 6:
                   //
                   // Deal with filename if any
                   //
                   if (fit.group(6) != null){
                       StringAndIntRecord record = new StringAndIntRecord(
                                                        fit.group(6),
                                                        0);
                       record.turnAllCircumflex(false);
                       String returnedString = record.getVmstoUnixFilespec();
                       if (MAX_MATCH == 10 && returnedString == null &&
                            fit.group(6).charAt(fit.group(6).length()-1) == '^' 
                            && fit.group(7).compareTo(".") == 0){
                           String correctedString = fit.group(6) + fit.group(7);
                           record = new StringAndIntRecord(
                                                        correctedString,
                                                        0);
                           record.turnAllCircumflex(false);
                           returnedString = record.getVmstoUnixFilespec();
                       }    
                       if (returnedString == null) {
                           return null;
                       }
                       vmstoUnixFilespec += returnedString;
                   }
                   break;
              case 8:
                   //
                   // Deal with the extension and file version if any
                   //
                   if (fit.group(8) != null) {
                       //
                       // Valid if filename is foo.1.2.3 in which
                       // case .3 is taken as a version number. In this
                       // case, fit.group(8) is "3" and fit.group(6) equals
                       // "foo.1.2" which leads to ODS-5 filename foo.1.2;3.
                       // Do not take into iaccount if filename is foo.1 in
                       // which case fit.group(6) is equal to "foo" and this
                       // fit.group(8) is "1", the ODS-5 filename is
                       // foo.1;. For []foo...1, the ODS-5 filename is
                       // foo^..;1 so the version must be taken into account.
                       // For []foo..102, the ODS-5 filename is foo^..102;
                       // so add in 102 as the file extension.
                       //
                       int lastIndexDot = vmstoUnixFilespec.lastIndexOf('.');
                       String numeric = new String("\\d+");

                       Pattern numericRegex = Pattern.compile(numeric);
                       Matcher matcher = numericRegex.matcher(fit.group(8));
                       if (matcher.matches()){
                           matcher = numericRegex.matcher(
                                         vmstoUnixFilespec.substring(
                                              lastIndexDot +1,
                                              vmstoUnixFilespec.length()));
                           if (matcher.matches()){
                               if (!checkVersionNumber(fit.group(8)))
                                   return null;
                               break;
                           }
                           else if (lastIndexDot != -1 &&
                                    lastIndexDot !=
                                    vmstoUnixFilespec.length() - 1){
                               if (!checkVersionNumber(fit.group(8)))
                                   return null;
                               break;
                           }
                           else if (lastIndexDot != -1 &&
                                    lastIndexDot ==
                                    vmstoUnixFilespec.length() - 1 &&
                               fit.group(6).charAt(
                                    fit.group(6).length() -2) != '^'){
                               if (!checkVersionNumber(fit.group(8)))
                                   return null;
                               vmstoUnixFilespec =
                                             vmstoUnixFilespec.substring(
                                                    0,
                                                    vmstoUnixFilespec.length() - 1);
                               break;
                           }
                       }
                       int lastSemiColon = fit.group(8).lastIndexOf(';');
                       if (lastSemiColon == - 1 || (lastSemiColon > 0 &&
                           fit.group(8).charAt(lastSemiColon - 1) == '^'))
                           lastSemiColon = fit.group(8).length();
                       else if (lastSemiColon != fit.group(8).length() - 1){
                           matcher = numericRegex.matcher(
                                               fit.group(8).substring(
                                                   lastSemiColon +1,
                                                   fit.group(8).length()));
                           if (!matcher.matches())
                               return null;
                           if (!checkVersionNumber(
                                             fit.group(8).substring(
                                                    lastSemiColon +1,
                                                    fit.group(8).length())))
                               return null;
                       }
                       StringAndIntRecord record = new StringAndIntRecord(
                                                   fit.group(8).substring(
                                                         0,lastSemiColon),0);
                       record.turnAllCircumflex(false);
                       String returnedString = record.getVmstoUnixFilespec();
                       if (returnedString == null)
                           return null;
                       if (lastSemiColon > 0)
                           vmstoUnixFilespec += "." + returnedString;
                   }
                   break;
           } // end switch   
       }
       return vmstoUnixFilespec;
   }
}
