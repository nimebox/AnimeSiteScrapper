package main

import (
	"encoding/json"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strconv"

	"github.com/vanng822/go-solr/solr"
)

type serviceElementJSON struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type animeElementJSON struct {
	ID        string `json:"id"`
	Title     string `json:"title"`
	SourceURL string `json:"url"`
	ImageB64  string `json:"imageB64"`
	Updated   string `json:"updated"`
}

type animeImageElementJSON struct {
	ID       string `json:"id"`
	ImageB64 string `json:"imageB64"`
}

type animeListResponseJSON struct {
	Start    int                `json:"start"`
	Total    int                `json:"total"`
	Elements []animeElementJSON `json:"elements"`
}

type animeEpisodeElementJSON struct {
	ID        string `json:"id"`
	AnimeID   string `json:"animeId"`
	Title     string `json:"title"`
	SourceURL string `json:"url"`
}

type episodeListResponseJSON struct {
	Start    int                       `json:"start"`
	Total    int                       `json:"total"`
	Elements []animeEpisodeElementJSON `json:"elements"`
}

type animeEpisodePlayerElementJSON struct {
	ID         string `json:"id"`
	EpisodeID  string `json:"episodeId"`
	PlayerName string `json:"playerName"`
	URL        string `json:"url"`
}

type episodePlayerListResponseJSON struct {
	Start    int                             `json:"start"`
	Total    int                             `json:"total"`
	Elements []animeEpisodePlayerElementJSON `json:"elements"`
}

type apiConfig struct {
	HTTPAddr string               `json:"httpAddr"`
	SolrAddr string               `json:"solrAddr"`
	AuthKey  string               `json:"authKey"`
	Services []serviceElementJSON `json:"services"`
}

var config apiConfig

func loadConfig() error {
	if os.Getenv("IN_DOCKER") == "1" {
		println("I'm in Docker :)")

		ao := serviceElementJSON{
			ID:   "ao",
			Name: "Anime-Odcinki.pl",
		}

		ad := serviceElementJSON{
			ID:   "ad",
			Name: "animedesu.pl",
		}

		services := make([]serviceElementJSON, 0)
		services = append(services, ao)
		services = append(services, ad)

		config = apiConfig{
			HTTPAddr: "0.0.0.0:3010",
			SolrAddr: os.Getenv("SOLR_ADDR"),
			AuthKey:  os.Getenv("AUTH_KEY"),
			Services: services,
		}

	} else {
		configFile, err := os.Open("config.json")
		if err != nil {
			return err
		}
		defer configFile.Close()

		byteValue, err := ioutil.ReadAll(configFile)
		if err != nil {
			return err
		}

		err = json.Unmarshal(byteValue, &config)
		if err != nil {
			return err
		}
	}

	return nil
}

func firstValidate(w http.ResponseWriter, r *http.Request) bool {
	if r.Method != "GET" {
		http.Error(w, "", http.StatusMethodNotAllowed)
		return false
	} else if r.Header.Get("KEY") != config.AuthKey {
		http.Error(w, "", http.StatusUnauthorized)
		return false
	}

	return true
}

func existService(id string) bool {
	retValue := false

	for _, s := range config.Services {
		if s.ID == id {
			retValue = true
		}
	}

	return retValue
}

func validateService(w http.ResponseWriter, r *http.Request) (string, bool) {
	if len(r.URL.Query()["serviceId"]) <= 0 || !existService(r.URL.Query()["serviceId"][0]) {
		http.Error(w, "Invalid serviceId param is empty or not exist", http.StatusBadRequest)
		return "", false
	}

	return r.URL.Query()["serviceId"][0], true
}

func parseStartParam(r *http.Request) int {
	var start = 0

	if len(r.URL.Query()["start"]) > 0 && r.URL.Query()["start"][0] != "" {
		i, err := strconv.Atoi(r.URL.Query()["start"][0])
		if err == nil {
			start = i
		}
	}

	return start
}

func parseIDParam(w http.ResponseWriter, r *http.Request, paramName string) (string, bool) {
	if len(r.URL.Query()[paramName]) <= 0 || r.URL.Query()[paramName][0] == "" {
		http.Error(w, "Invalid "+paramName+" param is empty or not exist", http.StatusBadRequest)
		return "", false
	}
	return r.URL.Query()[paramName][0], true
}

func writeJSONHTTP(w http.ResponseWriter, v interface{}) {
	data, err := json.Marshal(v)
	if err != nil {
		log.Print("*ERROR* Failed to marshall response: " + err.Error())
		http.Error(w, "Failed to marshall response.", http.StatusInternalServerError)
		return
	}

	w.Write(data)
}

func getDataFromDB(serviceID string, queryPattern string, start int, fieldList string, w http.ResponseWriter) (*solr.SolrResult, bool) {
	sial, sErr := solr.NewSolrInterface(config.SolrAddr, serviceID)
	if sErr != nil {
		log.Print("*ERROR* Failed connect to DB " + sErr.Error())
		http.Error(w, "Cannot connect to DB.", http.StatusInternalServerError)
		return nil, false
	}

	query := solr.NewQuery()
	query.Q(queryPattern)
	query.Sort("title asc")
	query.Start(start)
	query.FieldList(fieldList)
	s := sial.Search(query)
	rs, qErr := s.Result(nil)

	if qErr != nil {
		log.Print("*ERROR* Failed get data from DB " + qErr.Error())
		http.Error(w, "Cannot get data from DB.", http.StatusInternalServerError)
		return nil, false
	}

	return rs, true
}

//Endpoints

func servicesList(w http.ResponseWriter, r *http.Request) {
	if !firstValidate(w, r) {
		return
	}

	writeJSONHTTP(w, config.Services)
}

func getAnimeList(w http.ResponseWriter, r *http.Request) {
	if !firstValidate(w, r) {
		return
	}

	var serviceID, valid = validateService(w, r)
	if !valid {
		return
	}

	var titlePattern = "*"

	if len(r.URL.Query()["title"]) > 0 && r.URL.Query()["title"][0] != "" {
		titlePattern = r.URL.Query()["title"][0] + "*"
	}

	var imagesField = ""

	if len(r.URL.Query()["withImage"]) > 0 && r.URL.Query()["withImage"][0] == "true" {
		imagesField = " imageB64"
	}

	var start = parseStartParam(r)

	var animeList = animeListResponseJSON{}

	rs, qComplete := getDataFromDB(serviceID, "title:"+titlePattern+" AND type:0", start, "id, title, url, updated"+imagesField, w)

	if !qComplete {
		return
	}

	animeList.Start = rs.Results.Start
	animeList.Total = rs.Results.NumFound

	for _, el := range rs.Results.Docs {

		var image = el["imageB64"]
		if image == nil {
			image = ""
		}

		var updated = el["updated"]
		if updated == nil {
			updated = ""
		}

		var ae = animeElementJSON{el["id"].(string), el["title"].(string), el["url"].(string), image.(string), updated.(string)}

		animeList.Elements = append(animeList.Elements, ae)
	}

	writeJSONHTTP(w, animeList)
}

func getAnimeImage(w http.ResponseWriter, r *http.Request) {
	if !firstValidate(w, r) {
		return
	}

	var serviceID, valid = validateService(w, r)
	if !valid {
		return
	}

	var animeID, validAnimeID = parseIDParam(w, r, "animeId")
	if !validAnimeID {
		return
	}

	rs, qComplete := getDataFromDB(serviceID, "id:"+animeID+" AND type:0", 0, "id, imageB64", w)

	if !qComplete {
		return
	}

	if len(rs.Results.Docs) < 1 {
		http.Error(w, "Not found image for thi animeId.", http.StatusNotFound)
		return
	}

	writeJSONHTTP(w, animeImageElementJSON{rs.Results.Docs[0]["id"].(string), rs.Results.Docs[0]["imageB64"].(string)})
}

func getEpisodeList(w http.ResponseWriter, r *http.Request) {
	if !firstValidate(w, r) {
		return
	}

	var serviceID, validServiceID = validateService(w, r)
	if !validServiceID {
		return
	}

	var animeID, validAnimeID = parseIDParam(w, r, "animeId")
	if !validAnimeID {
		return
	}

	var start = parseStartParam(r)

	var episodeList = episodeListResponseJSON{}

	rs, qComplete := getDataFromDB(serviceID, "animeId:"+animeID+" AND type:1", start, "id, animeId, title, url", w)

	if !qComplete {
		return
	}

	episodeList.Start = rs.Results.Start
	episodeList.Total = rs.Results.NumFound

	for _, el := range rs.Results.Docs {
		var ee = animeEpisodeElementJSON{el["id"].(string), el["animeId"].(string), el["title"].(string), el["url"].(string)}

		episodeList.Elements = append(episodeList.Elements, ee)
	}

	writeJSONHTTP(w, episodeList)
}

func getEpisodePlayersList(w http.ResponseWriter, r *http.Request) {
	if !firstValidate(w, r) {
		return
	}

	var serviceID, valid = validateService(w, r)
	if !valid {
		return
	}

	var episodeID, validEpisodeID = parseIDParam(w, r, "episodeId")
	if !validEpisodeID {
		return
	}

	var start = parseStartParam(r)

	var playersList = episodePlayerListResponseJSON{}

	rs, qComplete := getDataFromDB(serviceID, "episodeId:"+episodeID+" AND type:2", start, "id, episodeId, title, url", w)

	if !qComplete {
		return
	}

	playersList.Start = rs.Results.Start
	playersList.Total = rs.Results.NumFound

	for _, el := range rs.Results.Docs {
		var ee = animeEpisodePlayerElementJSON{el["id"].(string), el["episodeId"].(string), el["title"].(string), el["url"].(string)}

		playersList.Elements = append(playersList.Elements, ee)
	}

	writeJSONHTTP(w, playersList)
}

func main() {
	log.Print("Loading config..")
	err := loadConfig()
	if err != nil {
		log.Print("*ERROR* Failed to load config " + err.Error())
		return
	}

	log.Print("Configuration loaded.")

	http.HandleFunc("/v1/services", servicesList)
	http.HandleFunc("/v1/animeList", getAnimeList)
	http.HandleFunc("/v1/animeImage", getAnimeImage)
	http.HandleFunc("/v1/episodeList", getEpisodeList)
	http.HandleFunc("/v1/playersList", getEpisodePlayersList)

	log.Print("Started at : " + config.HTTPAddr)
	log.Fatal(http.ListenAndServe(config.HTTPAddr, nil))
}
