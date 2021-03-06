package edu.knowitall.taggers.tag

import java.lang.reflect.InvocationTargetException
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet
import java.util.Iterator
import java.util.List
import java.util.Map
import java.util.Set
import java.util.TreeMap
import java.util.regex.Matcher
import java.util.regex.Pattern
import com.google.common.base.Predicate
import com.google.common.collect.ImmutableList
import edu.knowitall.tool.typer.Type
import edu.knowitall.taggers.LinkedType
import edu.knowitall.tool.chunk.ChunkedToken
import edu.knowitall.tool.stem.Lemmatized
import edu.washington.cs.knowitall.logic.ArgFactory
import edu.washington.cs.knowitall.logic.LogicExpression
import edu.washington.cs.knowitall.regex.Expression.BaseExpression
import edu.washington.cs.knowitall.regex.Expression.NamedGroup
import edu.washington.cs.knowitall.regex.ExpressionFactory
import edu.washington.cs.knowitall.regex.Match
import edu.washington.cs.knowitall.regex.RegularExpression
import edu.knowitall.openregex
import edu.knowitall.taggers.pattern.PatternBuilder
import edu.knowitall.taggers.pattern.TypedToken
import scala.collection.JavaConverters._
import edu.knowitall.taggers.TypeHelper
import edu.knowitall.tool.tokenize.Tokenizer
import edu.knowitall.taggers.NamedGroupType
import edu.knowitall.repr.sentence.Sentence
import edu.knowitall.repr.sentence.Chunked
import edu.knowitall.repr.sentence

/**
 * *
 * Run a token-based pattern over the text and tag matches.
 *
 * @author schmmd
 *
 */
class PatternTagger(patternTaggerName: String, expressions: Seq[String]) extends Tagger[Sentence with Chunked with sentence.Lemmatized] {
  override def name = patternTaggerName
  override def source = null

  val patterns: Seq[openregex.Pattern[PatternBuilder.Token]] = this.compile(expressions)

  protected def this(name: String) {
    this(name, null: Seq[String])
  }

  private def compile(expressions: Seq[String]) = {
    expressions map PatternBuilder.compile
  }

  override def findTags(sentence: TheSentence) = {
    this.findTagsWithTypes(sentence, Seq.empty[Type])
  }

  /**
   * This method overrides Tagger's default implementation. This
   * implementation uses information from the Types that have been assigned to
   * the sentence so far.
   */
  override def findTagsWithTypes(sentence: TheSentence,
    originalTags: Seq[Type]): Seq[Type] = {

    // create a java set of the original tags
    val originalTagSet = originalTags.toSet

    // convert tokens to TypedTokens
    val typedTokens = for ((token, i) <- sentence.lemmatizedTokens.zipWithIndex) yield {
      new TypedToken(token, i, originalTagSet.filter(_.tokenInterval contains i))
    }

    val tags = for {
      pattern <- patterns
      tag <- this.findTags(typedTokens, sentence, pattern)
    } yield (tag)

    return tags
  }

  /**
   * This is a helper method that creates the Type objects from a given
   * pattern and a List of TypedTokens.
   *
   * Matching groups will create a type with the name or index
   * appended to the name.
   *
   * @param typedTokenSentence
   * @param sentence
   * @param pattern
   * @return
   */
  protected def findTags(typedTokenSentence: Seq[TypedToken],
    sentence: TheSentence,
    pattern: openregex.Pattern[TypedToken]) = {

    var tags = Seq.empty[Type]

    val matches = pattern.findAll(typedTokenSentence);
    for (m <- matches) {
      val groupSize = m.groups.size
      for (i <- 0 until groupSize) {
        val group = m.groups(i);

        val tokens = sentence.lemmatizedTokens.slice(group.interval.start, group.interval.end).map(_.token)
        val text = Tokenizer.originalText(tokens, tokens.head.offsets.start)
        val tag = group.expr match {
          // create the main type for the group
          case _ if i == 0 =>
            Type(this.name, this.source, group.interval, text)
          case namedGroup: NamedGroup[_] =>
            val name = this.name + "." + namedGroup.name
            new NamedGroupType(namedGroup.name,Type(name, this.source, group.interval, text), tags.headOption)
          case _ =>
            val name = this.name + "." + i
            new LinkedType(Type(name, this.source, group.interval, text), tags.headOption)
        }
        tags = tags :+ tag
      }
    }

    tags
  }
}
