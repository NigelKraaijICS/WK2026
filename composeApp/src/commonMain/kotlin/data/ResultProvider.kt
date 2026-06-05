package data

import model.Match

interface ResultProvider {
    fun getResults(): List<Match>
}
