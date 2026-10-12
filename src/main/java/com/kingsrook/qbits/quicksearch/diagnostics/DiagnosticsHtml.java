/*
 * Copyright 2026 Kingsrook, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kingsrook.qbits.quicksearch.diagnostics;


import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;


/*******************************************************************************
 ** A small HTML builder for the diagnostic screens (process HTML blocks, the
 ** validation report field and the diagnostics widget). Every text value is
 ** escaped. Layout rules: one column of blocks capped at a readable width,
 ** no fixed widths, and long values (JSON, ids, text) wrap instead of
 ** scrolling, so a screen reads from 360px to 3200px without an inner or
 ** horizontal scrollbar. Colours are mid-tone so they read in light and dark
 ** themes; status is always also spelled out in text.
 *******************************************************************************/
public class DiagnosticsHtml
{
   public static final int MAX_JSON_CHARS = 60_000;

   private static final ObjectMapper PRETTY = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

   private static final String WRAP = "overflow-wrap:anywhere;word-break:break-word;";

   private final StringBuilder html = new StringBuilder();



   /*******************************************************************************
    ** Severity of a check or a line, rendered as a coloured label.
    *******************************************************************************/
   public enum Level
   {
      OK("OK", "#16a34a"),
      INFO("INFO", "#2563eb"),
      WARN("WARN", "#d97706"),
      FAIL("FAIL", "#dc2626");

      private final String label;
      private final String color;



      /*******************************************************************************
       ** Constructor
       *******************************************************************************/
      Level(String label, String color)
      {
         this.label = label;
         this.color = color;
      }



      /*******************************************************************************
       ** Getter for label
       *******************************************************************************/
      public String getLabel()
      {
         return (label);
      }
   }



   /*******************************************************************************
    ** Escape text for HTML element content and attribute values.
    *******************************************************************************/
   public static String escape(Object value)
   {
      if(value == null)
      {
         return ("");
      }
      String        text = String.valueOf(value);
      StringBuilder out  = new StringBuilder(text.length() + 16);
      for(int i = 0; i < text.length(); i++)
      {
         char c = text.charAt(i);
         switch(c)
         {
            case '<' -> out.append("&lt;");
            case '>' -> out.append("&gt;");
            case '&' -> out.append("&amp;");
            case '"' -> out.append("&quot;");
            case '\'' -> out.append("&#39;");
            default -> out.append(c);
         }
      }
      return (out.toString());
   }



   /*******************************************************************************
    ** Escape text but keep OpenSearch highlight tags (em) as mark elements.
    *******************************************************************************/
   public static String highlight(String fragment)
   {
      return (escape(fragment).replace("&lt;em&gt;", "<mark>").replace("&lt;/em&gt;", "</mark>"));
   }



   /*******************************************************************************
    ** A coloured status label.
    *******************************************************************************/
   public static String badge(Level level)
   {
      return ("<strong style=\"color:" + level.color + "\">" + level.label + "</strong>");
   }



   /*******************************************************************************
    ** Open the root block.
    *******************************************************************************/
   public DiagnosticsHtml open()
   {
      html.append("<div style=\"max-width:72rem;").append(WRAP).append("\">");
      return (this);
   }



   /*******************************************************************************
    ** Close the root block.
    *******************************************************************************/
   public DiagnosticsHtml close()
   {
      html.append("</div>");
      return (this);
   }



   /*******************************************************************************
    ** A section heading.
    *******************************************************************************/
   public DiagnosticsHtml heading(String text)
   {
      html.append("<h3>").append(escape(text)).append("</h3>");
      return (this);
   }



   /*******************************************************************************
    ** A sub-heading.
    *******************************************************************************/
   public DiagnosticsHtml subheading(String text)
   {
      html.append("<h4>").append(escape(text)).append("</h4>");
      return (this);
   }



   /*******************************************************************************
    ** A paragraph of escaped text.
    *******************************************************************************/
   public DiagnosticsHtml paragraph(String text)
   {
      html.append("<p>").append(escape(text)).append("</p>");
      return (this);
   }



   /*******************************************************************************
    ** A paragraph starting with a status label.
    *******************************************************************************/
   public DiagnosticsHtml status(Level level, String text)
   {
      html.append("<p>").append(badge(level)).append(" ").append(escape(text)).append("</p>");
      return (this);
   }



   /*******************************************************************************
    ** A list of checks: each line a status label and text.
    *******************************************************************************/
   public DiagnosticsHtml checks(List<Check> checks)
   {
      if(checks == null || checks.isEmpty())
      {
         return (this);
      }
      html.append("<ul>");
      for(Check check : checks)
      {
         html.append("<li>").append(badge(check.level())).append(" <strong>").append(escape(check.name())).append("</strong>");
         if(check.message() != null)
         {
            html.append(": ").append(escape(check.message()));
         }
         html.append("</li>");
      }
      html.append("</ul>");
      return (this);
   }



   /*******************************************************************************
    ** A list of label: value lines (values escaped). Null values show as
    ** "none".
    *******************************************************************************/
   public DiagnosticsHtml facts(List<String[]> facts)
   {
      html.append("<ul>");
      for(String[] fact : facts)
      {
         html.append("<li><strong>").append(escape(fact[0])).append(":</strong> ")
            .append(fact.length > 1 && fact[1] != null ? escape(fact[1]) : "<em>none</em>").append("</li>");
      }
      html.append("</ul>");
      return (this);
   }



   /*******************************************************************************
    ** A bulleted list of pre-built HTML items (callers escape their parts).
    *******************************************************************************/
   public DiagnosticsHtml rawList(List<String> itemsHtml)
   {
      if(itemsHtml == null || itemsHtml.isEmpty())
      {
         return (this);
      }
      html.append("<ul>");
      for(String item : itemsHtml)
      {
         html.append("<li>").append(item).append("</li>");
      }
      html.append("</ul>");
      return (this);
   }



   /*******************************************************************************
    ** A preformatted block that wraps (no horizontal scrolling).
    *******************************************************************************/
   public DiagnosticsHtml pre(String text)
   {
      html.append("<pre style=\"white-space:pre-wrap;").append(WRAP).append("\">").append(escape(text)).append("</pre>");
      return (this);
   }



   /*******************************************************************************
    ** A collapsed block with a summary line and pretty-printed JSON inside.
    *******************************************************************************/
   public DiagnosticsHtml jsonDetails(String summary, String json)
   {
      html.append("<details><summary>").append(escape(summary)).append("</summary>");
      pre(prettyJson(json));
      html.append("</details>");
      return (this);
   }



   /*******************************************************************************
    ** A collapsed block with a summary line and preformatted text inside.
    *******************************************************************************/
   public DiagnosticsHtml textDetails(String summary, String text)
   {
      html.append("<details><summary>").append(escape(summary)).append("</summary>");
      pre(text);
      html.append("</details>");
      return (this);
   }



   /*******************************************************************************
    ** Append pre-built HTML (callers escape their parts).
    *******************************************************************************/
   public DiagnosticsHtml raw(String rawHtml)
   {
      html.append(rawHtml);
      return (this);
   }



   /*******************************************************************************
    ** Pretty-print JSON (or return the text as-is when it is not JSON),
    ** truncated to MAX_JSON_CHARS.
    *******************************************************************************/
   public static String prettyJson(String json)
   {
      if(json == null)
      {
         return ("");
      }
      String pretty;
      try
      {
         pretty = PRETTY.writeValueAsString(PRETTY.readTree(json));
      }
      catch(Exception e)
      {
         pretty = json;
      }
      return (pretty.length() > MAX_JSON_CHARS ? pretty.substring(0, MAX_JSON_CHARS) + "\n... (truncated at " + MAX_JSON_CHARS + " characters)" : pretty);
   }



   /*******************************************************************************
    ** Pretty-print a JSON node.
    *******************************************************************************/
   public static String prettyJson(JsonNode node)
   {
      return (node == null ? "" : prettyJson(node.toString()));
   }



   /*******************************************************************************
    ** The built HTML.
    *******************************************************************************/
   @Override
   public String toString()
   {
      return (html.toString());
   }



   /*******************************************************************************
    ** One check: name, level and message.
    *******************************************************************************/
   public record Check(String name, Level level, String message)
   {
   }

}
