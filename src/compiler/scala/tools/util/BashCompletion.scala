/* NSC -- new Scala compiler
 * Copyright 2006-2010 LAMP/EPFL
 * @author  Paul Phillips
 */

package scala.tools
package util

import nsc.{ Global, Settings }
import Settings._

/** Examines Settings and generates a bash completion file
 *  containing both bells and whistles.
 */
object BashCompletion {
  val completionTemplate = """
# Bash Scala completion
#
# Add this file to /etc/bash_completion.d/ (or your local equivalent)
# or place a line like this in your .bashrc or .profile:
#
#   .  /path/to/file/scala_completion.sh
#
# For more information, see:
#
#   http://bash-completion.alioth.debian.org/
#
# This file is generated by running scala.tools.util.BashCompletion.
#

SCALA_PHASES="@@PHASES@@"
SCALA_PHASE_SETTINGS=( @@PHASE_SETTINGS@@ )
SCALA_OPTIONS="@@OPTIONS@@"
SCALA_OPTIONS_EXPANDED="@@OPTIONS_EXPANDED@@"

_scala_completion()
{
  local cur prev opts colonprefixes
  
  COMPREPLY=()
  opts=""
  cur="${COMP_WORDS[COMP_CWORD]}"
  prev="${COMP_WORDS[COMP_CWORD-1]}"
  colonprefixes=${cur%"${cur##*:}"}
  
  # special case escaping madness because bash treats : as a separator.
  case "${cur}" in
    -*:*)
      precolon=$(echo "${cur}" | sed 's/:.*//g')
      
      for p in ${SCALA_PHASE_SETTINGS[@]}; do
        if [[ "${precolon}" == "${p}" ]] ; then
          cur=$(echo "${cur}" | sed 's/.*://g')  # cut cur down to postcolon part
          opts=${SCALA_PHASES}
    	  fi
    	done
    	
    	if [ "${opts}" == "" ] ; then
    	  opts=${SCALA_OPTIONS_EXPANDED}
    	fi
    	;;
  esac
  
  if [ "${opts}" == "" ] ; then
	  opts=${SCALA_OPTIONS}
  fi
  
  COMPREPLY=( $(compgen -W "${opts}" -- ${cur}) )
  
  local i=${#COMPREPLY[*]}
  while [ $((--i)) -ge 0 ]; do
    COMPREPLY[$i]=${COMPREPLY[$i]#"$colonprefixes"}
  done
  
  return 0
}

_scala_commands()
{
@@PROGRAMS@@
}
_scala_commands
  """.trim

  private lazy val settings = new Settings()

  val phaseNames = "all" :: (new Global(settings) phaseNames)
  val phaseSettings = settings.visibleSettings partialMap { case x: PhasesSetting => "\"" + x.name + "\"" }

  def settingStrings(s: Setting, expanded: Boolean) = s match {
    case x: ChoiceSetting       => if (expanded) x.choices map (x.name + ":" + _) else List(x.name + ":")
    case x: PhasesSetting       => List(x.name + ":")
    case x                      => List(x.name)
  }
 
  /** We embed one list which stops at : and another where all choice settings are expanded out
   *  to include the choices.
   */
  def settingNames = settings.visibleSettings.toList flatMap (x => settingStrings(x, false)) sorted
  def settingNamesExpanded = settings.visibleSettings.toList flatMap (x => settingStrings(x, true)) sorted
  
  def commandForName(name: String) = "  complete -o default -F _scala_completion " + name + "\n"
  def interpolate(template: String, what: (String, String)*) =
    what.foldLeft(template) {
      case (text, (key, value)) =>
        val token = "@@" + key + "@@"
        
        (text indexOf token) match {
          case -1   => error("Token '%s' does not exist." format token)
          case idx  => (text take idx) + value + (text drop idx drop token.length)
        }
    }
  
  def create(cmds: List[String]) = {
    interpolate(completionTemplate,
      "PROGRAMS"          -> (cmds map commandForName mkString ""),
      "OPTIONS"           -> (settingNames mkString " "),
      "OPTIONS_EXPANDED"  -> (settingNamesExpanded mkString " "),
      "PHASES"            -> (phaseNames mkString " "),
      "PHASE_SETTINGS"    -> (phaseSettings mkString " ")
    )
  }

  def main(args: Array[String]): Unit = {
    val commands = if (args.isEmpty) List("fsc", "scala", "scalac", "scaladoc") else args.toList
    val result = create(commands)
    if (result contains "@@")
      error("Some tokens were not replaced: text is " + result)
    
    println(result)
  }
}